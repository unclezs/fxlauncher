package fxlauncher;


public class UpdateInfo {
    private String whatNew;
    private String version;
    private Boolean update;

    public UpdateInfo(String whatNew, String version, Boolean update) {
        this.whatNew = whatNew;
        this.version = version;
        this.update = update;
    }

    public String getWhatNew() {
        return whatNew;
    }

    public void setWhatNew(String whatNew) {
        this.whatNew = whatNew;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Boolean getUpdate() {
        return update;
    }

    public void setUpdate(Boolean update) {
        this.update = update;
    }
}
