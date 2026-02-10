package io.datalens.outlook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.ComThread;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@Component
@Profile("outlook")
public class OutlookClient {

    private static final Logger log = LoggerFactory.getLogger(OutlookClient.class);
    private static final int OL_FOLDER_INBOX = 6;
    private static final Pattern RE_FWD_PREFIX = Pattern.compile("^(?:RE|FW|FWD):\\s*", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OutlookConfig config;
    private final ObjectMapper objectMapper;
    private final ExecutorService comExecutor;

    public OutlookClient(OutlookConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        // Single-thread executor — all COM calls must happen on the same STA thread
        this.comExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "outlook-com-thread");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void init() {
        String dllName = "jacob-1.18-x64.dll"; // Change to jacob-1.17-x64.dll for JPMorgan internal
        // Search multiple classpath locations:
        // 1. /native/ — extracted by maven-dependency-plugin during build
        // 2. JAR root — bundled inside com.hynnet:jacob dependency
        String[] searchPaths = {"/native/" + dllName, "/" + dllName};

        InputStream dllStream = null;
        String foundPath = null;
        for (String path : searchPaths) {
            dllStream = getClass().getResourceAsStream(path);
            if (dllStream != null) {
                foundPath = path;
                break;
            }
        }

        if (dllStream != null) {
            try (InputStream is = dllStream) {
                Path tempDll = Files.createTempFile("jacob", ".dll");
                Files.copy(is, tempDll, StandardCopyOption.REPLACE_EXISTING);
                tempDll.toFile().deleteOnExit();
                System.setProperty("jacob.dll.path", tempDll.toAbsolutePath().toString());
                log.info("JACOB DLL loaded from classpath: {} -> {}", foundPath, tempDll);
            } catch (Exception e) {
                log.error("Failed to extract JACOB DLL: {}", e.getMessage(), e);
            }
        } else {
            log.warn("JACOB DLL not found on classpath. Ensure the com.hynnet:jacob dependency is present in pom.xml.");
        }
    }

    @PreDestroy
    public void shutdown() {
        comExecutor.shutdownNow();
    }

    /**
     * Execute a callable on the dedicated COM STA thread.
     */
    private <T> T executeOnComThread(Callable<T> task) throws Exception {
        Future<T> future = comExecutor.submit(() -> {
            ComThread.InitSTA();
            try {
                return task.call();
            } finally {
                ComThread.Release();
            }
        });
        try {
            return future.get(config.getSearchTimeoutSeconds() + 10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw (e.getCause() instanceof Exception) ? (Exception) e.getCause() : new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("Outlook COM operation timed out after " + (config.getSearchTimeoutSeconds() + 10) + "s");
        }
    }

    public JsonNode checkConnection() throws Exception {
        return executeOnComThread(() -> {
            ObjectNode result = objectMapper.createObjectNode();
            ActiveXComponent outlookApp = new ActiveXComponent("Outlook.Application");
            Dispatch namespace = Dispatch.call(outlookApp, "GetNamespace", "MAPI").toDispatch();

            result.put("connected", true);
            result.put("outlook_version", Dispatch.get(outlookApp, "Version").getString());

            // Check personal mailbox
            try {
                Dispatch personalInbox = Dispatch.call(namespace, "GetDefaultFolder", OL_FOLDER_INBOX).toDispatch();
                String personalFolderPath = Dispatch.get(personalInbox, "FolderPath").getString();
                result.put("personal_mailbox", personalFolderPath);
                result.put("personal_mailbox_accessible", true);
            } catch (Exception e) {
                result.put("personal_mailbox_accessible", false);
                result.put("personal_mailbox_error", e.getMessage());
            }

            // Check shared mailbox
            String sharedEmail = config.getSharedMailboxEmail();
            if (sharedEmail != null && !sharedEmail.isBlank()) {
                try {
                    Dispatch recipient = Dispatch.call(namespace, "CreateRecipient", sharedEmail).toDispatch();
                    Dispatch.call(recipient, "Resolve");
                    boolean resolved = Dispatch.get(recipient, "Resolved").getBoolean();
                    if (resolved) {
                        Dispatch sharedInbox = Dispatch.call(namespace, "GetSharedDefaultFolder", recipient, OL_FOLDER_INBOX).toDispatch();
                        String sharedFolderPath = Dispatch.get(sharedInbox, "FolderPath").getString();
                        result.put("shared_mailbox", sharedFolderPath);
                        result.put("shared_mailbox_accessible", true);
                    } else {
                        result.put("shared_mailbox_accessible", false);
                        result.put("shared_mailbox_error", "Could not resolve recipient: " + sharedEmail);
                    }
                } catch (Exception e) {
                    result.put("shared_mailbox_accessible", false);
                    result.put("shared_mailbox_error", e.getMessage());
                }
            }

            return result;
        });
    }

    public JsonNode searchEmails(String searchText, boolean includePersonal, boolean includeShared) throws Exception {
        return executeOnComThread(() -> {
            ActiveXComponent outlookApp;
            try {
                outlookApp = new ActiveXComponent("Outlook.Application");
            } catch (Exception e) {
                log.error("Failed to connect to Outlook COM. Is Outlook running? Security policy may be blocking programmatic access.", e);
                throw new RuntimeException("Cannot connect to Outlook. Ensure Outlook Desktop is running and "
                        + "programmatic access is allowed in Trust Center > Macro Settings.");
            }
            Dispatch namespace = Dispatch.call(outlookApp, "GetNamespace", "MAPI").toDispatch();

            List<ObjectNode> allEmails = new ArrayList<>();

            // Search personal mailbox
            if (includePersonal && config.isSearchPersonalMailbox()) {
                try {
                    Dispatch personalInbox = Dispatch.call(namespace, "GetDefaultFolder", OL_FOLDER_INBOX).toDispatch();
                    String folderPath = Dispatch.get(personalInbox, "FolderPath").getString();
                    List<ObjectNode> personalResults = searchFolder(outlookApp, namespace, personalInbox, folderPath, searchText, "personal");
                    allEmails.addAll(personalResults);
                    log.info("Personal mailbox search returned {} emails", personalResults.size());
                } catch (Exception e) {
                    log.error("Error searching personal mailbox: {}", e.getMessage(), e);
                }
            }

            // Search shared mailbox
            String sharedEmail = config.getSharedMailboxEmail();
            if (includeShared && config.isSearchSharedMailbox() && sharedEmail != null && !sharedEmail.isBlank()) {
                try {
                    Dispatch recipient = Dispatch.call(namespace, "CreateRecipient", sharedEmail).toDispatch();
                    Dispatch.call(recipient, "Resolve");
                    boolean resolved = Dispatch.get(recipient, "Resolved").getBoolean();
                    if (resolved) {
                        Dispatch sharedInbox = Dispatch.call(namespace, "GetSharedDefaultFolder", recipient, OL_FOLDER_INBOX).toDispatch();
                        String folderPath = Dispatch.get(sharedInbox, "FolderPath").getString();
                        List<ObjectNode> sharedResults = searchFolder(outlookApp, namespace, sharedInbox, folderPath, searchText, "shared");
                        allEmails.addAll(sharedResults);
                        log.info("Shared mailbox search returned {} emails", sharedResults.size());
                    }
                } catch (Exception e) {
                    log.error("Error searching shared mailbox: {}", e.getMessage(), e);
                }
            }

            return buildResponse(searchText, allEmails);
        });
    }

    private List<ObjectNode> searchFolder(ActiveXComponent outlookApp, Dispatch namespace,
                                          Dispatch folder, String folderPath,
                                          String searchText, String mailboxType) {
        List<ObjectNode> results = new ArrayList<>();

        // Try AdvancedSearch first
        try {
            results = advancedSearch(outlookApp, folderPath, searchText, mailboxType);
        } catch (Exception e) {
            log.warn("AdvancedSearch failed, falling back to Restrict: {}", e.getMessage());
        }

        // Fallback to Restrict filter if AdvancedSearch returned nothing
        if (results.isEmpty()) {
            try {
                results = restrictSearch(folder, searchText, mailboxType);
            } catch (Exception e) {
                log.error("Restrict search also failed: {}", e.getMessage(), e);
            }
        }

        return results;
    }

    private List<ObjectNode> advancedSearch(ActiveXComponent outlookApp, String folderPath,
                                            String searchText, String mailboxType) throws Exception {
        String escapedText = searchText.replace("\"", "\"\"");
        String daslQuery = "urn:schemas:httpmail:subject ci_phrasematch '" + escapedText + "' "
                + "OR urn:schemas:httpmail:textdescription ci_phrasematch '" + escapedText + "'";

        String scope = "'" + folderPath + "'";
        String tag = "DataLensSearch_" + System.currentTimeMillis();

        Dispatch search = Dispatch.call(outlookApp, "AdvancedSearch", scope, daslQuery, config.isSearchAllFolders(), tag).toDispatch();

        // Poll for results with timeout
        int timeoutMs = config.getSearchTimeoutSeconds() * 1000;
        int elapsed = 0;
        int pollInterval = 250;

        while (elapsed < timeoutMs) {
            try {
                Dispatch searchResults = Dispatch.get(search, "Results").toDispatch();
                int count = Dispatch.get(searchResults, "Count").getInt();
                if (count > 0) {
                    return extractEmails(searchResults, count, mailboxType);
                }
                // If Tag property indicates completion, break early
                break;
            } catch (Exception e) {
                // Search still running, wait and retry
                Thread.sleep(pollInterval);
                elapsed += pollInterval;
            }
        }

        // Final attempt to get results
        try {
            Dispatch searchResults = Dispatch.get(search, "Results").toDispatch();
            int count = Dispatch.get(searchResults, "Count").getInt();
            if (count > 0) {
                return extractEmails(searchResults, count, mailboxType);
            }
        } catch (Exception e) {
            log.debug("No results from AdvancedSearch: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    private List<ObjectNode> restrictSearch(Dispatch folder, String searchText, String mailboxType) {
        List<ObjectNode> results = new ArrayList<>();

        String escapedText = searchText.replace("\"", "\"\"");
        String filter = "@SQL=\"urn:schemas:httpmail:subject\" LIKE '%" + escapedText + "%' "
                + "OR \"urn:schemas:httpmail:textdescription\" LIKE '%" + escapedText + "%'";

        Dispatch items = Dispatch.get(folder, "Items").toDispatch();
        Dispatch filteredItems = Dispatch.call(items, "Restrict", filter).toDispatch();
        int count = Dispatch.get(filteredItems, "Count").getInt();

        int limit = Math.min(count, config.getMaxSearchResults());
        for (int i = 1; i <= limit; i++) {
            try {
                Dispatch item = Dispatch.call(filteredItems, "Item", i).toDispatch();
                ObjectNode email = extractSingleEmail(item, mailboxType);
                if (email != null) {
                    results.add(email);
                }
            } catch (Exception e) {
                log.debug("Error extracting email at index {}: {}", i, e.getMessage());
            }
        }

        return results;
    }

    private List<ObjectNode> extractEmails(Dispatch searchResults, int count, String mailboxType) {
        List<ObjectNode> emails = new ArrayList<>();
        int limit = Math.min(count, config.getMaxSearchResults());

        for (int i = 1; i <= limit; i++) {
            try {
                Dispatch item = Dispatch.call(searchResults, "Item", i).toDispatch();
                ObjectNode email = extractSingleEmail(item, mailboxType);
                if (email != null) {
                    emails.add(email);
                }
            } catch (Exception e) {
                log.debug("Error extracting search result at index {}: {}", i, e.getMessage());
            }
        }

        return emails;
    }

    private ObjectNode extractSingleEmail(Dispatch item, String mailboxType) {
        try {
            ObjectNode email = objectMapper.createObjectNode();

            email.put("subject", safeGetString(item, "Subject"));
            email.put("sender_name", safeGetString(item, "SenderName"));
            email.put("sender_email", safeGetString(item, "SenderEmailAddress"));
            email.put("received_time", safeGetDate(item, "ReceivedTime"));
            email.put("mailbox_type", mailboxType);
            email.put("importance", safeGetInt(item, "Importance"));
            email.put("unread", safeGetBoolean(item, "UnRead"));
            email.put("attachments_count", safeGetInt(item, "Attachments.Count"));
            email.put("entry_id", safeGetString(item, "EntryID"));

            // Get recipients
            ArrayNode recipientList = objectMapper.createArrayNode();
            try {
                Dispatch recipients = Dispatch.get(item, "Recipients").toDispatch();
                int recipCount = Dispatch.get(recipients, "Count").getInt();
                for (int r = 1; r <= recipCount; r++) {
                    Dispatch recip = Dispatch.call(recipients, "Item", r).toDispatch();
                    recipientList.add(safeGetString(recip, "Name"));
                }
            } catch (Exception e) {
                log.debug("Error reading recipients: {}", e.getMessage());
            }
            email.set("recipients", recipientList);

            // Get body — truncate and strip HTML
            String body = safeGetString(item, "Body");
            if (body != null) {
                body = stripHtmlTags(body);
                if (body.length() > config.getMaxBodyChars()) {
                    body = body.substring(0, config.getMaxBodyChars()) + "... [truncated]";
                }
            }
            email.put("body", body);

            return email;
        } catch (Exception e) {
            log.debug("Error extracting email: {}", e.getMessage());
            return null;
        }
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

        // Date range
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

        // Mailbox distribution
        ObjectNode mailboxDist = objectMapper.createObjectNode();
        long personalCount = allEmails.stream().filter(e -> "personal".equals(e.path("mailbox_type").asText())).count();
        long sharedCount = allEmails.stream().filter(e -> "shared".equals(e.path("mailbox_type").asText())).count();
        mailboxDist.put("personal", personalCount);
        mailboxDist.put("shared", sharedCount);
        summary.set("mailbox_distribution", mailboxDist);

        // Unique participants
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
        // Repeatedly strip Re:/Fwd:/FW: prefixes
        String previous;
        do {
            previous = normalized;
            normalized = RE_FWD_PREFIX.matcher(normalized).replaceFirst("").trim();
        } while (!normalized.equals(previous));
        return normalized;
    }

    private String safeGetString(Dispatch item, String property) {
        try {
            Variant v = Dispatch.get(item, property);
            return v != null ? v.getString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int safeGetInt(Dispatch item, String property) {
        try {
            Variant v = Dispatch.get(item, property);
            return v != null ? v.getInt() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean safeGetBoolean(Dispatch item, String property) {
        try {
            Variant v = Dispatch.get(item, property);
            return v != null && v.getBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private String safeGetDate(Dispatch item, String property) {
        try {
            Variant v = Dispatch.get(item, property);
            if (v != null) {
                Date date = v.getJavaDate();
                if (date != null) {
                    LocalDateTime ldt = date.toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime();
                    return ldt.format(DATE_FORMAT);
                }
            }
        } catch (Exception e) {
            // Fall back to string representation
            try {
                return Dispatch.get(item, property).getString();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String stripHtmlTags(String text) {
        if (text == null) return null;
        return text.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }
}
