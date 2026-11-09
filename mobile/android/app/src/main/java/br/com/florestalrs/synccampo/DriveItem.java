package br.com.florestalrs.synccampo;

import java.io.File;

public class DriveItem {
    private String id;
    private String name;
    private String mimeType;
    private long size;
    private String sizeFormatted;
    private String modifiedTime;
    private boolean isFolder;
    private String parentId;
    private String folderPath;
    private boolean isCached;
    private boolean isFavorite;
    private String subtitleInfo;
    private File localFile;
    private String versionKey;

    public DriveItem() {
    }

    /** Chave de versão (checksum + data de modificação), igual à usada pelo BackgroundSyncWorker. */
    public String getVersionKey() {
        return versionKey != null ? versionKey : "";
    }

    public void setVersionKey(String versionKey) {
        this.versionKey = versionKey;
    }

    public DriveItem(String id, String name, String mimeType, long size, String sizeFormatted, String modifiedTime, boolean isFolder, String parentId, String folderPath) {
        this.id = id;
        this.name = name;
        this.mimeType = mimeType;
        this.size = size;
        this.sizeFormatted = sizeFormatted;
        this.modifiedTime = modifiedTime;
        this.isFolder = isFolder;
        this.parentId = parentId;
        this.folderPath = folderPath;
    }

    public String getId() {
        return id != null ? id : "";
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name != null ? name : "";
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMimeType() {
        return mimeType != null ? mimeType : "";
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getSizeFormatted() {
        return sizeFormatted != null ? sizeFormatted : "--";
    }

    public void setSizeFormatted(String sizeFormatted) {
        this.sizeFormatted = sizeFormatted;
    }

    public String getModifiedTime() {
        return modifiedTime != null ? modifiedTime : "";
    }

    public void setModifiedTime(String modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public boolean isFolder() {
        return isFolder || "application/vnd.google-apps.folder".equals(mimeType);
    }

    public void setFolder(boolean folder) {
        isFolder = folder;
    }

    public String getParentId() {
        return parentId != null ? parentId : "root";
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getFolderPath() {
        return folderPath != null ? folderPath : "";
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public boolean isCached() {
        return isCached;
    }

    public void setCached(boolean cached) {
        isCached = cached;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public String getSubtitleInfo() {
        return subtitleInfo != null ? subtitleInfo : "";
    }

    public void setSubtitleInfo(String subtitleInfo) {
        this.subtitleInfo = subtitleInfo;
    }

    public File getLocalFile() {
        return localFile;
    }

    public void setLocalFile(File localFile) {
        this.localFile = localFile;
    }
}
