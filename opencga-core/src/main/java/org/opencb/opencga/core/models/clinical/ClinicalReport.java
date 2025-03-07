package org.opencb.opencga.core.models.clinical;

public class ClinicalReport {

    private String title;
    private String overview;
    private String discussion;

    private String logo;
    private String signedBy;
    private String signature;
    private String date;

    public ClinicalReport() {
    }

    public ClinicalReport(String title, String overview, String discussion, String logo, String signedBy,
                          String signature, String date) {
        this.title = title;
        this.overview = overview;
        this.discussion = discussion;
        this.logo = logo;
        this.signedBy = signedBy;
        this.signature = signature;
        this.date = date;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ClinicalReport{");
        sb.append("title='").append(title).append('\'');
        sb.append(", overview='").append(overview).append('\'');
        sb.append(", discussion='").append(discussion).append('\'');
        sb.append(", logo='").append(logo).append('\'');
        sb.append(", signedBy='").append(signedBy).append('\'');
        sb.append(", signature='").append(signature).append('\'');
        sb.append(", date='").append(date).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public String getTitle() {
        return title;
    }

    public ClinicalReport setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getOverview() {
        return overview;
    }

    public ClinicalReport setOverview(String overview) {
        this.overview = overview;
        return this;
    }

    public String getDiscussion() {
        return discussion;
    }

    public ClinicalReport setDiscussion(String discussion) {
        this.discussion = discussion;
        return this;
    }

    public String getLogo() {
        return logo;
    }

    public ClinicalReport setLogo(String logo) {
        this.logo = logo;
        return this;
    }

    public String getSignedBy() {
        return signedBy;
    }

    public ClinicalReport setSignedBy(String signedBy) {
        this.signedBy = signedBy;
        return this;
    }

    public String getSignature() {
        return signature;
    }

    public ClinicalReport setSignature(String signature) {
        this.signature = signature;
        return this;
    }

    public String getDate() {
        return date;
    }

    public ClinicalReport setDate(String date) {
        this.date = date;
        return this;
    }
}
