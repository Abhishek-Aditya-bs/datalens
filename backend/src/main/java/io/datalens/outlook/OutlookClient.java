package io.datalens.outlook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Outlook Desktop client that uses PowerShell to execute searches via Outlook COM.
 * Uses AdvancedSearch (backed by Windows Search Index) for fast, non-blocking queries.
 * No Jacob DLL or native dependencies required — runs entirely via PowerShell process execution.
 */
@Component
@Profile("outlook")
public class OutlookClient {

    private static final Logger log = LoggerFactory.getLogger(OutlookClient.class);
    private static final Pattern RE_FWD_PREFIX = Pattern.compile("^(?:RE|FW|FWD):\\s*", Pattern.CASE_INSENSITIVE);

    private final OutlookConfig config;
    private final ObjectMapper objectMapper;

    private static final String CHECK_CONNECTION_SCRIPT = """
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            $ErrorActionPreference = 'Stop'
            $sharedMailbox = $env:DL_SHARED_MAILBOX
            $result = [ordered]@{ connected = $false }
            try {
                $outlook = New-Object -ComObject Outlook.Application
                $ns = $outlook.GetNamespace('MAPI')
                $result.connected = $true
                $result.outlook_version = $outlook.Version
                try {
                    $inbox = $ns.GetDefaultFolder(6)
                    $result.personal_mailbox = $inbox.FolderPath
                    $result.personal_mailbox_accessible = $true
                } catch {
                    $result.personal_mailbox_accessible = $false
                    $result.personal_mailbox_error = $_.Exception.Message
                }
                if ($sharedMailbox) {
                    try {
                        $recip = $ns.CreateRecipient($sharedMailbox)
                        $recip.Resolve()
                        if ($recip.Resolved) {
                            $sharedInbox = $ns.GetSharedDefaultFolder($recip, 6)
                            $result.shared_mailbox = $sharedInbox.FolderPath
                            $result.shared_mailbox_accessible = $true
                        } else {
                            $result.shared_mailbox_accessible = $false
                            $result.shared_mailbox_error = "Could not resolve: $sharedMailbox"
                        }
                    } catch {
                        $result.shared_mailbox_accessible = $false
                        $result.shared_mailbox_error = $_.Exception.Message
                    }
                }
            } catch {
                $result.connected = $false
                $result.error = $_.Exception.Message
            }
            $result | ConvertTo-Json -Depth 3 -Compress
            """;

    private static final String SEARCH_EMAILS_SCRIPT = """
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            $ErrorActionPreference = 'Stop'
            $searchText = $env:DL_SEARCH_TEXT
            $maxResults = [int]$env:DL_MAX_RESULTS
            $maxBodyChars = [int]$env:DL_MAX_BODY_CHARS
            $includePersonal = $env:DL_INCLUDE_PERSONAL -eq 'true'
            $includeShared = $env:DL_INCLUDE_SHARED -eq 'true'
            $sharedMailbox = $env:DL_SHARED_MAILBOX
            $searchAllFolders = $env:DL_SEARCH_ALL_FOLDERS -eq 'true'
            $searchTimeout = [int]$env:DL_SEARCH_TIMEOUT
            try {
                $outlook = New-Object -ComObject Outlook.Application
                $ns = $outlook.GetNamespace('MAPI')
                $allEmails = [System.Collections.ArrayList]::new()
                $escaped = $searchText -replace '"', '""'
                $filter = 'urn:schemas:httpmail:subject ci_phrasematch "' + $escaped + '" OR urn:schemas:httpmail:textdescription ci_phrasematch "' + $escaped + '"'
                function Extract-Emails {
                    param([object]$Results, [string]$MailboxType, [int]$Limit)
                    $count = [Math]::Min($Results.Count, $Limit)
                    for ($i = 1; $i -le $count; $i++) {
                        try {
                            $item = $Results.Item($i)
                            $body = $item.Body
                            if ($body) {
                                $body = $body -replace '<[^>]+>', '' -replace '\\s+', ' '
                                $body = $body.Trim()
                                if ($body.Length -gt $maxBodyChars) {
                                    $body = $body.Substring(0, $maxBodyChars) + '... [truncated]'
                                }
                            }
                            $recipNames = @()
                            try {
                                for ($r = 1; $r -le $item.Recipients.Count; $r++) {
                                    $recipNames += [string]$item.Recipients.Item($r).Name
                                }
                            } catch {}
                            $receivedTime = $null
                            try { $receivedTime = $item.ReceivedTime.ToString('yyyy-MM-dd HH:mm:ss') } catch {}
                            $email = [ordered]@{
                                subject = [string]$item.Subject
                                sender_name = [string]$item.SenderName
                                sender_email = [string]$item.SenderEmailAddress
                                received_time = $receivedTime
                                mailbox_type = $MailboxType
                                importance = [int]$item.Importance
                                unread = [bool]$item.UnRead
                                attachments_count = [int]$item.Attachments.Count
                                entry_id = [string]$item.EntryID
                                recipients = @($recipNames)
                                body = $body
                            }
                            [void]$allEmails.Add($email)
                        } catch {}
                    }
                }
                function Search-Mailbox {
                    param([string]$Scope, [string]$MailboxType)
                    $remaining = $maxResults - $allEmails.Count
                    if ($remaining -le 0) { return }
                    $tag = 'DL_' + $MailboxType + '_' + (Get-Date -Format 'yyyyMMddHHmmssfff')
                    $search = $outlook.AdvancedSearch($Scope, $filter, $searchAllFolders, $tag)
                    $sw = [System.Diagnostics.Stopwatch]::StartNew()
                    $complete = $false
                    while (-not $complete -and $sw.Elapsed.TotalSeconds -lt $searchTimeout) {
                        Start-Sleep -Milliseconds 200
                        try { $complete = $search.SearchComplete } catch { break }
                    }
                    try {
                        Extract-Emails -Results $search.Results -MailboxType $MailboxType -Limit $remaining
                    } catch {}
                }
                if ($includePersonal) {
                    try {
                        $inbox = $ns.GetDefaultFolder(6)
                        $scope = "'" + $inbox.FolderPath + "'"
                        Search-Mailbox -Scope $scope -MailboxType 'personal'
                    } catch {}
                }
                if ($includeShared -and $sharedMailbox) {
                    try {
                        $recip = $ns.CreateRecipient($sharedMailbox)
                        $recip.Resolve()
                        if ($recip.Resolved) {
                            $sharedInbox = $ns.GetSharedDefaultFolder($recip, 6)
                            $scope = "'" + $sharedInbox.FolderPath + "'"
                            Search-Mailbox -Scope $scope -MailboxType 'shared'
                        }
                    } catch {}
                }
                $emailsJson = '[]'
                if ($allEmails.Count -gt 0) {
                    $raw = @($allEmails) | ConvertTo-Json -Depth 4 -Compress
                    if ($allEmails.Count -eq 1) {
                        $emailsJson = '[' + $raw + ']'
                    } else {
                        $emailsJson = $raw
                    }
                }
                '{"status":"success","emails":' + $emailsJson + '}'
            } catch {
                @{ status = 'error'; error = $_.Exception.Message } | ConvertTo-Json -Compress
            }
            """;

    public OutlookClient(OutlookConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        log.info("Outlook integration active (PowerShell-based, using Windows Search Index via AdvancedSearch)");
    }

    public JsonNode checkConnection() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("DL_SHARED_MAILBOX", nullSafe(config.getSharedMailboxEmail()));
        return executePowerShell(CHECK_CONNECTION_SCRIPT, env);
    }

    public JsonNode searchEmails(String searchText, boolean includePersonal, boolean includeShared) throws Exception {
        log.info("Searching Outlook via PowerShell: query='{}', personal={}, shared={}", searchText, includePersonal, includeShared);

        Map<String, String> env = new HashMap<>();
        env.put("DL_SEARCH_TEXT", searchText);
        env.put("DL_MAX_RESULTS", String.valueOf(config.getMaxSearchResults()));
        env.put("DL_MAX_BODY_CHARS", String.valueOf(config.getMaxBodyChars()));
        env.put("DL_INCLUDE_PERSONAL", String.valueOf(includePersonal && config.isSearchPersonalMailbox()));
        env.put("DL_INCLUDE_SHARED", String.valueOf(includeShared && config.isSearchSharedMailbox()));
        env.put("DL_SHARED_MAILBOX", nullSafe(config.getSharedMailboxEmail()));
        env.put("DL_SEARCH_ALL_FOLDERS", String.valueOf(config.isSearchAllFolders()));
        env.put("DL_SEARCH_TIMEOUT", String.valueOf(config.getSearchTimeoutSeconds()));

        JsonNode psResult = executePowerShell(SEARCH_EMAILS_SCRIPT, env);

        if ("error".equals(psResult.path("status").asText())) {
            throw new RuntimeException("Outlook search failed: " + psResult.path("error").asText("Unknown error"));
        }

        List<ObjectNode> allEmails = new ArrayList<>();
        JsonNode emailsNode = psResult.path("emails");
        if (emailsNode.isArray()) {
            for (JsonNode email : emailsNode) {
                if (email.isObject()) {
                    allEmails.add((ObjectNode) email);
                }
            }
        }

        log.info("PowerShell search returned {} emails", allEmails.size());
        return buildResponse(searchText, allEmails);
    }

    private JsonNode executePowerShell(String script, Map<String, String> envVars) throws Exception {
        byte[] scriptBytes = script.getBytes(StandardCharsets.UTF_16LE);
        String encodedScript = Base64.getEncoder().encodeToString(scriptBytes);

        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-EncodedCommand", encodedScript
        );
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);

        long timeoutSeconds = config.getSearchTimeoutSeconds() + 15;
        Process process = pb.start();

        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
            try (InputStream is = process.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("PowerShell timed out after " + timeoutSeconds + "s");
        }

        String output = outputFuture.get(5, TimeUnit.SECONDS).trim();
        if (output.isBlank()) {
            throw new RuntimeException("PowerShell returned empty output (exit code: " + process.exitValue() + ")");
        }

        // Skip any non-JSON preamble (warnings, BOM, etc.)
        int jsonStart = output.indexOf('{');
        if (jsonStart < 0) {
            throw new RuntimeException("No JSON in PowerShell output: " + output.substring(0, Math.min(200, output.length())));
        }
        if (jsonStart > 0) {
            log.debug("Skipped {} chars of PowerShell preamble", jsonStart);
            output = output.substring(jsonStart);
        }

        return objectMapper.readTree(output);
    }

    private JsonNode buildResponse(String searchText, List<ObjectNode> allEmails) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "success");
        response.put("search_text", searchText);

        // Group by conversation (normalized subject)
        Map<String, List<ObjectNode>> conversations = new LinkedHashMap<>();
        for (ObjectNode email : allEmails) {
            String subject = email.path("subject").asText("");
            String normalizedSubject = normalizeSubject(subject);
            conversations.computeIfAbsent(normalizedSubject, k -> new ArrayList<>()).add(email);
        }

        // Sort each conversation chronologically
        for (List<ObjectNode> emails : conversations.values()) {
            emails.sort(Comparator.comparing(e -> e.path("received_time").asText("")));
        }

        // Build summary
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("total_emails", allEmails.size());
        summary.put("conversations", conversations.size());

        String earliest = allEmails.stream()
                .map(e -> e.path("received_time").asText(""))
                .filter(s -> !s.isEmpty())
                .min(String::compareTo)
                .orElse("");
        String latest = allEmails.stream()
                .map(e -> e.path("received_time").asText(""))
                .filter(s -> !s.isEmpty())
                .max(String::compareTo)
                .orElse("");
        if (!earliest.isEmpty()) summary.put("date_range_start", earliest);
        if (!latest.isEmpty()) summary.put("date_range_end", latest);

        ObjectNode mailboxDist = objectMapper.createObjectNode();
        long personalCount = allEmails.stream().filter(e -> "personal".equals(e.path("mailbox_type").asText())).count();
        long sharedCount = allEmails.stream().filter(e -> "shared".equals(e.path("mailbox_type").asText())).count();
        mailboxDist.put("personal", personalCount);
        mailboxDist.put("shared", sharedCount);
        summary.set("mailbox_distribution", mailboxDist);

        Set<String> participants = new LinkedHashSet<>();
        for (ObjectNode email : allEmails) {
            String sender = email.path("sender_name").asText("");
            if (!sender.isEmpty()) participants.add(sender);
            email.path("recipients").forEach(r -> {
                String name = r.asText("");
                if (!name.isEmpty()) participants.add(name);
            });
        }
        ArrayNode participantsArray = objectMapper.createArrayNode();
        participants.forEach(participantsArray::add);
        summary.set("participants", participantsArray);

        response.set("summary", summary);

        // Build conversations array
        ArrayNode conversationsArray = objectMapper.createArrayNode();
        int convId = 1;
        for (Map.Entry<String, List<ObjectNode>> entry : conversations.entrySet()) {
            ObjectNode conv = objectMapper.createObjectNode();
            conv.put("conversation_id", convId++);
            conv.put("subject", entry.getKey());
            conv.put("email_count", entry.getValue().size());

            Set<String> convParticipants = new LinkedHashSet<>();
            for (ObjectNode email : entry.getValue()) {
                String sender = email.path("sender_name").asText("");
                if (!sender.isEmpty()) convParticipants.add(sender);
            }
            ArrayNode convParticipantsArray = objectMapper.createArrayNode();
            convParticipants.forEach(convParticipantsArray::add);
            conv.set("participants", convParticipantsArray);

            ArrayNode emailsArray = objectMapper.createArrayNode();
            entry.getValue().forEach(emailsArray::add);
            conv.set("emails", emailsArray);

            conversationsArray.add(conv);
        }
        response.set("conversations", conversationsArray);

        return response;
    }

    private String normalizeSubject(String subject) {
        if (subject == null) return "";
        String normalized = subject.trim();
        String previous;
        do {
            previous = normalized;
            normalized = RE_FWD_PREFIX.matcher(normalized).replaceFirst("").trim();
        } while (!normalized.equals(previous));
        return normalized;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
