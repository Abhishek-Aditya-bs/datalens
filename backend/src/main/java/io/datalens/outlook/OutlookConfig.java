package io.datalens.outlook;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("outlook")
@ConfigurationProperties(prefix = "datalens.outlook")
public class OutlookConfig {

    private String sharedMailboxEmail;
    private boolean searchPersonalMailbox = true;
    private boolean searchSharedMailbox = true;
    private int maxSearchResults = 50;
    private int searchTimeoutSeconds = 30;
    private boolean searchAllFolders = false;
    private int maxBodyChars = 5000;

    public String getSharedMailboxEmail() { return sharedMailboxEmail; }
    public void setSharedMailboxEmail(String sharedMailboxEmail) { this.sharedMailboxEmail = sharedMailboxEmail; }
    public boolean isSearchPersonalMailbox() { return searchPersonalMailbox; }
    public void setSearchPersonalMailbox(boolean searchPersonalMailbox) { this.searchPersonalMailbox = searchPersonalMailbox; }
    public boolean isSearchSharedMailbox() { return searchSharedMailbox; }
    public void setSearchSharedMailbox(boolean searchSharedMailbox) { this.searchSharedMailbox = searchSharedMailbox; }
    public int getMaxSearchResults() { return maxSearchResults; }
    public void setMaxSearchResults(int maxSearchResults) { this.maxSearchResults = maxSearchResults; }
    public int getSearchTimeoutSeconds() { return searchTimeoutSeconds; }
    public void setSearchTimeoutSeconds(int searchTimeoutSeconds) { this.searchTimeoutSeconds = searchTimeoutSeconds; }
    public boolean isSearchAllFolders() { return searchAllFolders; }
    public void setSearchAllFolders(boolean searchAllFolders) { this.searchAllFolders = searchAllFolders; }
    public int getMaxBodyChars() { return maxBodyChars; }
    public void setMaxBodyChars(int maxBodyChars) { this.maxBodyChars = maxBodyChars; }
}
