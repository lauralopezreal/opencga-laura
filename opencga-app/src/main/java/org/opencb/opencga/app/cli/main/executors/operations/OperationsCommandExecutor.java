package org.opencb.opencga.app.cli.main.executors.operations;

import org.apache.commons.lang3.StringUtils;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.opencga.app.cli.GeneralCliOptions;
import org.opencb.opencga.app.cli.internal.options.VariantCommandOptions;
import org.opencb.opencga.app.cli.main.executors.OpencgaCommandExecutor;
import org.opencb.opencga.app.cli.main.options.OperationsCommandOptions;
import org.opencb.opencga.client.exceptions.ClientException;
import org.opencb.opencga.core.api.ParamConstants;
import org.opencb.opencga.core.common.JacksonUtils;
import org.opencb.opencga.core.common.YesNoAuto;
import org.opencb.opencga.core.config.storage.CellBaseConfiguration;
import org.opencb.opencga.core.config.storage.SampleIndexConfiguration;
import org.opencb.opencga.core.models.job.Job;
import org.opencb.opencga.core.models.operations.variant.*;
import org.opencb.opencga.core.models.variant.*;
import org.opencb.opencga.core.response.RestResponse;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import static org.opencb.opencga.app.cli.main.options.OperationsCommandOptions.*;

public class OperationsCommandExecutor extends OpencgaCommandExecutor {

    private OperationsCommandOptions operationsCommandOptions;

    public OperationsCommandExecutor(OperationsCommandOptions operationsCommandOptions) {
        super(operationsCommandOptions.commonCommandOptions);
        this.operationsCommandOptions = operationsCommandOptions;
    }


    @Override
    public void execute() throws Exception {
        logger.debug("Executing variant command line");

        String subCommandString = getParsedSubCommand(operationsCommandOptions.jCommander);
        RestResponse<?> queryResponse = null;
        switch (subCommandString) {
            case CELLBASE_CONFIGURE:
                queryResponse = cellbaseConfigure();
                break;
            case VARIANT_CONFIGURE:
                queryResponse = variantConfigure();
                break;
            case VARIANT_INDEX:
                queryResponse = variantIndex();
                break;
            case VARIANT_DELETE:
                queryResponse = variantFileDelete();
                break;
            case VARIANT_SAMPLE_DELETE:
                queryResponse = variantSampleDelete();
                break;
            case VARIANT_INDEX_LAUNCHER:
                queryResponse = variantIndexLauncher();
                break;
            case VARIANT_STATS_INDEX:
                queryResponse = variantStatsIndex();
                break;
            case VARIANT_SECONDARY_INDEX:
                queryResponse = variantSecondaryIndex();
                break;
            case VARIANT_SECONDARY_INDEX_DELETE:
                queryResponse = variantSecondaryIndexDelete();
                break;
            case VARIANT_ANNOTATION_INDEX:
                queryResponse = variantAnnotationIndex();
                break;
            case VARIANT_ANNOTATION_SAVE:
                queryResponse = variantAnnotationSave();
                break;
            case VARIANT_ANNOTATION_DELETE:
                queryResponse = variantAnnotationDelete();
                break;
            case VARIANT_SCORE_INDEX:
                queryResponse = variantScoreIndex();
                break;
            case VARIANT_SCORE_DELETE:
                queryResponse = variantScoreDelete();
                break;
            case VARIANT_FAMILY_INDEX:
                queryResponse = variantFamilyIndex();
                break;
            case VARIANT_SAMPLE_INDEX:
                queryResponse = variantSampleIndex();
                break;
            case VARIANT_SAMPLE_INDEX_CONFIGURE:
                queryResponse = variantSampleIndexConfigure();
                break;
            case VARIANT_AGGREGATE:
                queryResponse = variantAggregate();
                break;
            case VARIANT_FAMILY_AGGREGATE:
                queryResponse = variantAggregateFamily();
                break;
            case VariantCommandOptions.JulieRunCommandOptions.JULIE_RUN_COMMAND:
                queryResponse = julie();
                break;
            default:
                logger.error("Subcommand not valid");
                break;
        }

//        ObjectMapper objectMapper = new ObjectMapper();
//        System.out.println(objectMapper.writeValueAsString(queryResponse.getResponse()));

        createOutput(queryResponse);

    }

    private RestResponse<Job> cellbaseConfigure() throws ClientException {
        OperationsCommandOptions.CellbaseConfigureCommandOptions cliOptions = operationsCommandOptions.cellbaseConfigure;

        ObjectMap params = getParams(cliOptions.project, null);
        params.put("annotationUpdate", cliOptions.annotationUpdate);
        params.putIfNotNull("annotationSaveId", cliOptions.annotationSaveId);
        return openCGAClient.getVariantOperationClient().configureCellbase(
                new CellBaseConfiguration(cliOptions.url, cliOptions.version),
                params);
    }


    private RestResponse<ObjectMap> variantConfigure() throws ClientException {
        OperationsCommandOptions.VariantConfigureCommandOptions cliOptions = operationsCommandOptions.variantConfigure;

        ObjectMap params = getParams(cliOptions.project, cliOptions.study);

        return openCGAClient.getVariantOperationClient().configureVariant(new ObjectMap(cliOptions.commonOptions.params), params);
    }

    private RestResponse<Job> variantIndex() throws ClientException {
        OperationsCommandOptions.VariantIndexCommandOptions cliOptions = operationsCommandOptions.variantIndex;
        return openCGAClient.getVariantOperationClient().indexVariant(
                new VariantIndexParams(
                        cliOptions.fileId,
                        cliOptions.genericVariantIndexOptions.resume,
                        null,
                        cliOptions.genericVariantIndexOptions.transform,
                        cliOptions.genericVariantIndexOptions.gvcf,
                        cliOptions.genericVariantIndexOptions.normalizationSkip,
                        cliOptions.genericVariantIndexOptions.referenceGenome,
                        cliOptions.genericVariantIndexOptions.family,
                        cliOptions.genericVariantIndexOptions.somatic,
                        cliOptions.genericVariantIndexOptions.load,
                        cliOptions.genericVariantIndexOptions.loadSplitData,
                        cliOptions.genericVariantIndexOptions.loadMultiFileData,
                        cliOptions.genericVariantIndexOptions.loadSampleIndex,
                        cliOptions.genericVariantIndexOptions.loadArchive,
                        cliOptions.genericVariantIndexOptions.loadHomRef,
                        cliOptions.genericVariantIndexOptions.postLoadCheck,
                        cliOptions.genericVariantIndexOptions.includeGenotype,
                        cliOptions.genericVariantIndexOptions.includeSampleData,
                        cliOptions.genericVariantIndexOptions.merge,
                        cliOptions.genericVariantIndexOptions.deduplicationPolicy,
                        cliOptions.genericVariantIndexOptions.calculateStats,
                        cliOptions.genericVariantIndexOptions.aggregated,
                        cliOptions.genericVariantIndexOptions.aggregationMappingFile,
                        cliOptions.genericVariantIndexOptions.annotate,
                        cliOptions.genericVariantIndexOptions.annotator,
                        cliOptions.genericVariantIndexOptions.overwriteAnnotations,
                        cliOptions.genericVariantIndexOptions.indexSearch,
                        cliOptions.skipIndexedFiles),
                getParams(cliOptions));
    }

    private RestResponse<Job> variantFileDelete() throws ClientException {
        OperationsCommandOptions.VariantFileDeleteCommandOptions cliOptions = operationsCommandOptions.variantFileDelete;

        return openCGAClient.getVariantOperationClient().deleteVariant(
                new VariantFileDeleteParams(
                        cliOptions.genericVariantDeleteOptions.file,
                        cliOptions.genericVariantDeleteOptions.resume),
                getParams(cliOptions));
    }

    private RestResponse<Job> variantSampleDelete() throws ClientException {
        OperationsCommandOptions.VariantSampleDeleteCommandOptions cliOptions = operationsCommandOptions.variantSampleDelete;

        return openCGAClient.getVariantOperationClient().deleteVariantSample(
                new VariantSampleDeleteParams(
                        cliOptions.sample,
                        cliOptions.resume),
                getParams(cliOptions));
    }

    private RestResponse<Job> variantIndexLauncher() throws ClientException {
        OperationsCommandOptions.VariantIndexLauncherCommandOptions cliOptions = operationsCommandOptions.variantIndexLauncher;

        ObjectMap params = getParams(cliOptions);

        return openCGAClient.getVariantOperationClient().launcherVariantIndex(
                new VariantFileIndexJobLauncherParams(
                        cliOptions.name,
                        cliOptions.directory,
                        cliOptions.resumeFailed,
                        cliOptions.ignoreFailed,
                        cliOptions.maxJobs,
                        VariantIndexParams.fromParams(VariantIndexParams.class, cliOptions.indexParams)), params);
    }

    private RestResponse<Job> variantStatsIndex() throws ClientException {
        OperationsCommandOptions.VariantStatsIndexCommandOptions cliOptions = operationsCommandOptions.variantStatsIndex;

        ObjectMap params = getParams(cliOptions);

        return openCGAClient.getVariantOperationClient().indexVariantStats(
                new VariantStatsIndexParams(
                        cliOptions.cohort,
                        cliOptions.region,
                        cliOptions.overwriteStats,
                        cliOptions.resume,
                        cliOptions.aggregated,
                        cliOptions.aggregationMappingFile), params);
    }

    private RestResponse<Job> variantSecondaryIndex() throws ClientException {
        OperationsCommandOptions.VariantSecondaryIndexCommandOptions cliOptions = operationsCommandOptions.variantSecondaryIndex;

        ObjectMap params = getParams(cliOptions.project, cliOptions.study);

        return openCGAClient.getVariantOperationClient().secondaryIndexVariant(
                new VariantSecondaryIndexParams(
                        cliOptions.region,
                        cliOptions.sample,
                        cliOptions.overwrite), params);
    }

    private RestResponse<Job> variantSecondaryIndexDelete() throws ClientException {
        OperationsCommandOptions.VariantSecondaryIndexDeleteCommandOptions cliOptions = operationsCommandOptions.variantSecondaryIndexDelete;

        ObjectMap params = getParams(cliOptions)
                .append("sample", cliOptions.sample);

        return openCGAClient.getVariantOperationClient().deleteVariantSecondaryIndex(params);
    }

    private RestResponse<Job> variantAnnotationIndex() throws ClientException {
        OperationsCommandOptions.VariantAnnotationIndexCommandOptions cliOptions = operationsCommandOptions.variantAnnotation;

        ObjectMap params = getParams(cliOptions.project, cliOptions.study);

        VariantAnnotationIndexParams body = new VariantAnnotationIndexParams(
                cliOptions.outdir,
                cliOptions.genericVariantAnnotateOptions.outputFileName,
                cliOptions.genericVariantAnnotateOptions.annotator == null
                        ? null
                        : cliOptions.genericVariantAnnotateOptions.annotator.toString(),
                cliOptions.genericVariantAnnotateOptions.overwriteAnnotations,
                cliOptions.genericVariantAnnotateOptions.region,
                cliOptions.genericVariantAnnotateOptions.create,
                cliOptions.genericVariantAnnotateOptions.load,
                cliOptions.genericVariantAnnotateOptions.customName,
                YesNoAuto.parse(cliOptions.genericVariantAnnotateOptions.sampleIndexAnnotation)
        );
        return openCGAClient.getVariantOperationClient().indexVariantAnnotation(body, params);
    }

    private RestResponse<Job> variantAnnotationSave() throws ClientException {
        OperationsCommandOptions.VariantAnnotationSaveCommandOptions cliOptions = operationsCommandOptions.variantAnnotationSave;

        ObjectMap params = getParams(cliOptions.project, null);

        return openCGAClient.getVariantOperationClient().saveVariantAnnotation(new VariantAnnotationSaveParams(cliOptions.annotationId), params);
    }

    private RestResponse<Job> variantAnnotationDelete() throws ClientException {
        OperationsCommandOptions.VariantAnnotationDeleteCommandOptions cliOptions = operationsCommandOptions.variantAnnotationDelete;

        ObjectMap params = getParams(cliOptions.project, null)
                .appendAll(new VariantAnnotationDeleteParams(cliOptions.annotationId).toObjectMap());

        return openCGAClient.getVariantOperationClient().deleteVariantAnnotation(params);
    }

    private RestResponse<Job> variantScoreIndex() throws ClientException {
        OperationsCommandOptions.VariantScoreIndexCommandOptions cliOptions = operationsCommandOptions.variantScoreIndex;

        ObjectMap params = getParams(cliOptions.study);

        return openCGAClient.getVariantOperationClient().indexVariantScore(
                new VariantScoreIndexParams(
                        cliOptions.scoreName,
                        cliOptions.cohort1,
                        cliOptions.cohort2,
                        cliOptions.input,
                        cliOptions.columns,
                        cliOptions.resume
                ), params);
    }

    private RestResponse<Job> variantScoreDelete() throws ClientException {
        OperationsCommandOptions.VariantScoreDeleteCommandOptions cliOptions = operationsCommandOptions.variantScoreDelete;

        ObjectMap params = getParams(cliOptions.study)
                .appendAll(new VariantScoreDeleteParams(
                        cliOptions.scoreName,
                        cliOptions.resume,
                        cliOptions.force).toObjectMap());

        return openCGAClient.getVariantOperationClient().deleteVariantScore(params);
    }

    private RestResponse<Job> variantFamilyIndex() throws ClientException {
        OperationsCommandOptions.VariantFamilyGenotypeIndexCommandOptions cliOptions = operationsCommandOptions.variantFamilyIndex;

        ObjectMap params = getParams(cliOptions);

        return openCGAClient.getVariantOperationClient().indexVariantFamily(
                new VariantFamilyIndexParams(
                        cliOptions.family,
                        cliOptions.overwrite,
                        cliOptions.skipIncompleteFamilies
                ), params);
    }

    private RestResponse<Job> variantSampleIndex() throws ClientException {
        OperationsCommandOptions.VariantSampleGenotypeIndexCommandOptions cliOptions = operationsCommandOptions.variantSampleIndex;

        ObjectMap params = getParams(cliOptions);

        return openCGAClient.getVariantOperationClient().indexVariantSample(
                new VariantSampleIndexParams(
                        cliOptions.sample,
                        cliOptions.buildIndex,
                        cliOptions.annotate,
                        cliOptions.overwrite
                ), params);
    }

    private RestResponse<Job> variantSampleIndexConfigure() throws ClientException, IOException {
        OperationsCommandOptions.VariantSampleIndexConfigureCommandOptions cliOptions = operationsCommandOptions.variantSampleIndexConfigure;

        ObjectMap params = getParams(cliOptions);
        params.put("skipRebuild", cliOptions.skipRebuild);

        SampleIndexConfiguration sampleIndex;
        if (cliOptions.sampleIndex.equals("default")) {
            sampleIndex = SampleIndexConfiguration.defaultConfiguration();
        } else {
            Path sampleIndexFile = Paths.get(cliOptions.sampleIndex);
            if (!sampleIndexFile.toFile().exists()) {
                throw new IOException("File '" + sampleIndexFile + "' not found!");
            }
            sampleIndex = JacksonUtils.getDefaultObjectMapper().readValue(sampleIndexFile.toFile(),
                    SampleIndexConfiguration.class);
        }

        return openCGAClient.getVariantOperationClient().configureSampleIndex(sampleIndex, params);
    }

    private RestResponse<Job> variantAggregate() throws ClientException {
        OperationsCommandOptions.VariantAggregateCommandOptions cliOptions = operationsCommandOptions.variantAggregate;

        ObjectMap params = getParams(cliOptions);

        return openCGAClient.getVariantOperationClient().aggregateVariant(
                new VariantAggregateParams(
                        cliOptions.aggregateCommandOptions.overwrite,
                        cliOptions.aggregateCommandOptions.resume
                ), params);
    }

    private RestResponse<Job> variantAggregateFamily() throws ClientException {
        OperationsCommandOptions.VariantFamilyAggregateCommandOptions cliOptions = operationsCommandOptions.variantAggregateFamily;

        ObjectMap params = getParams(cliOptions);

        return openCGAClient.getVariantOperationClient().aggregateVariantFamily(
                new VariantAggregateFamilyParams(
                        cliOptions.genericAggregateFamilyOptions.samples, cliOptions.genericAggregateFamilyOptions.resume
                ), params);
    }

    private RestResponse<Job> julie() throws ClientException {
        OperationsCommandOptions.JulieRunCommandOptions cliOptions = operationsCommandOptions.julieRun;

        ObjectMap params = getParams(cliOptions.julieCommandOptions.project, null);

        JulieParams toolParams = new JulieParams(StringUtils.isEmpty(cliOptions.julieCommandOptions.cohort)
                ? Collections.emptyList()
                : Arrays.asList(cliOptions.julieCommandOptions.cohort.split(",")),
                cliOptions.julieCommandOptions.region,
                cliOptions.julieCommandOptions.overwrite);

        return openCGAClient.getVariantOperationClient().runVariantJulie(toolParams, params);
    }


    private ObjectMap getParams(GeneralCliOptions.StudyOption option) {
        return getParams(null, option.study);
    }

    private ObjectMap getParams(String project, String study) {
        ObjectMap params = new ObjectMap(operationsCommandOptions.commonCommandOptions.params);
        params.putIfNotEmpty(ParamConstants.PROJECT_PARAM, project);
        params.putIfNotEmpty(ParamConstants.STUDY_PARAM, study);
        params.putIfNotEmpty(ParamConstants.JOB_ID, operationsCommandOptions.commonJobOptions.jobId);
        params.putIfNotEmpty(ParamConstants.JOB_DESCRIPTION, operationsCommandOptions.commonJobOptions.jobDescription);
        if (operationsCommandOptions.commonJobOptions.jobDependsOn != null) {
            params.put(ParamConstants.JOB_DEPENDS_ON, String.join(",", operationsCommandOptions.commonJobOptions.jobDependsOn));
        }
        if (operationsCommandOptions.commonJobOptions.jobTags != null) {
            params.put(ParamConstants.JOB_TAGS, String.join(",", operationsCommandOptions.commonJobOptions.jobTags));
        }

        return params;
    }
}
