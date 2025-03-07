package org.opencb.opencga.core.models.study;

import org.opencb.opencga.core.tools.ToolParams;

public class TemplateParams extends ToolParams {

    public static final String DESCRIPTION = "Template loader parameters.";

    private String id;
    private boolean overwrite;
    private boolean resume;

    public TemplateParams() {
    }

    public TemplateParams(String id, boolean overwrite, boolean resume) {
        this.id = id;
        this.overwrite = overwrite;
        this.resume = resume;
    }

    public String getId() {
        return id;
    }

    public TemplateParams setId(String id) {
        this.id = id;
        return this;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public TemplateParams setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
        return this;
    }

    public boolean isResume() {
        return resume;
    }

    public TemplateParams setResume(boolean resume) {
        this.resume = resume;
        return this;
    }
}
