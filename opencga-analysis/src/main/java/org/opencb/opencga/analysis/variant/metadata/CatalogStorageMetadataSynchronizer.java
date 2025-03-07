
/*
 * Copyright 2015-2020 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.analysis.variant.metadata;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mongodb.MongoServerException;
import org.apache.commons.collections4.CollectionUtils;
import org.opencb.biodata.models.variant.StudyEntry;
import org.opencb.biodata.models.variant.VariantFileMetadata;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.analysis.variant.operations.VariantIndexOperationTool;
import org.opencb.opencga.catalog.db.api.*;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.managers.CatalogManager;
import org.opencb.opencga.catalog.utils.Constants;
import org.opencb.opencga.catalog.utils.FileMetadataReader;
import org.opencb.opencga.catalog.utils.ParamUtils;
import org.opencb.opencga.core.common.TimeUtils;
import org.opencb.opencga.core.models.cohort.Cohort;
import org.opencb.opencga.core.models.cohort.CohortStatus;
import org.opencb.opencga.core.models.cohort.CohortUpdateParams;
import org.opencb.opencga.core.models.common.Enums;
import org.opencb.opencga.core.models.file.File;
import org.opencb.opencga.core.models.file.FileIndex;
import org.opencb.opencga.core.models.file.FileIndex.IndexStatus;
import org.opencb.opencga.core.models.job.Job;
import org.opencb.opencga.core.models.project.Project;
import org.opencb.opencga.core.models.project.ProjectOrganism;
import org.opencb.opencga.core.models.sample.Sample;
import org.opencb.opencga.core.models.sample.SampleReferenceParam;
import org.opencb.opencga.core.response.OpenCGAResult;
import org.opencb.opencga.storage.core.exceptions.StorageEngineException;
import org.opencb.opencga.storage.core.metadata.VariantStorageMetadataManager;
import org.opencb.opencga.storage.core.metadata.models.*;
import org.opencb.opencga.storage.core.variant.annotation.annotators.AbstractCellBaseVariantAnnotator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.opencb.opencga.catalog.db.api.FileDBAdaptor.QueryParams.ID;
import static org.opencb.opencga.catalog.db.api.FileDBAdaptor.QueryParams.URI;

/**
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class CatalogStorageMetadataSynchronizer {

    public static final QueryOptions INDEXED_FILES_QUERY_OPTIONS = new QueryOptions(QueryOptions.INCLUDE, Arrays.asList(
            FileDBAdaptor.QueryParams.NAME.key(),
            FileDBAdaptor.QueryParams.PATH.key(),
            FileDBAdaptor.QueryParams.URI.key(),
            FileDBAdaptor.QueryParams.SAMPLE_IDS.key(),
            FileDBAdaptor.QueryParams.INTERNAL_INDEX.key(),
            FileDBAdaptor.QueryParams.STUDY_UID.key()));
    public static final Query INDEXED_FILES_QUERY = new Query()
            .append(FileDBAdaptor.QueryParams.INTERNAL_INDEX_STATUS_NAME.key(), IndexStatus.READY)
            .append(FileDBAdaptor.QueryParams.BIOFORMAT.key(), File.Bioformat.VARIANT)
            .append(FileDBAdaptor.QueryParams.FORMAT.key(), Arrays.asList(File.Format.VCF.toString(), File.Format.GVCF.toString()));

    public static final Query RUNNING_INDEX_FILES_QUERY = new Query()
            .append(FileDBAdaptor.QueryParams.INTERNAL_INDEX_STATUS_NAME.key(), Arrays.asList(IndexStatus.LOADING, IndexStatus.INDEXING))
            .append(FileDBAdaptor.QueryParams.BIOFORMAT.key(), File.Bioformat.VARIANT)
            .append(FileDBAdaptor.QueryParams.FORMAT.key(), Arrays.asList(File.Format.VCF.toString(), File.Format.GVCF.toString()));

    public static final QueryOptions COHORT_QUERY_OPTIONS = new QueryOptions(QueryOptions.INCLUDE, Arrays.asList(
            CohortDBAdaptor.QueryParams.ID.key(),
            CohortDBAdaptor.QueryParams.INTERNAL_STATUS.key(),
            CohortDBAdaptor.QueryParams.SAMPLES.key() + "." + SampleDBAdaptor.QueryParams.ID.key()
    ));

    protected static Logger logger = LoggerFactory.getLogger(CatalogStorageMetadataSynchronizer.class);

    private final CatalogManager catalogManager;
    private final VariantStorageMetadataManager metadataManager;


    public CatalogStorageMetadataSynchronizer(CatalogManager catalogManager, VariantStorageMetadataManager metadataManager) {
        this.catalogManager = catalogManager;
        this.metadataManager = metadataManager;
    }

    public static void updateProjectMetadata(CatalogManager catalog, VariantStorageMetadataManager scm, String project, String sessionId)
            throws CatalogException, StorageEngineException {
        final Project p = catalog.getProjectManager().get(project,
                new QueryOptions(QueryOptions.INCLUDE, Arrays.asList(
                        ProjectDBAdaptor.QueryParams.ORGANISM.key(), ProjectDBAdaptor.QueryParams.CURRENT_RELEASE.key())),
                sessionId)
                .first();

        updateProjectMetadata(scm, p.getOrganism(), p.getCurrentRelease());
    }

    public static void updateProjectMetadata(VariantStorageMetadataManager scm, ProjectOrganism organism, int release)
            throws StorageEngineException {
        String scientificName = AbstractCellBaseVariantAnnotator.toCellBaseSpeciesName(organism.getScientificName());

        scm.updateProjectMetadata(projectMetadata -> {
            if (projectMetadata == null) {
                projectMetadata = new ProjectMetadata();
            }
            projectMetadata.setSpecies(scientificName);
            projectMetadata.setAssembly(organism.getAssembly());
            projectMetadata.setRelease(release);
            return projectMetadata;
        });
    }

    public StudyMetadata getStudyMetadata(String study) throws CatalogException {
        return metadataManager.getStudyMetadata(study);
    }

    /**
     * Updates catalog metadata from storage metadata.
     *
     * @param study                 StudyMetadata
     * @param files                 Files to update
     * @param sessionId             User session id
     * @return if there were modifications in catalog
     * @throws CatalogException     if there is an error with catalog
     */
    public boolean synchronizeCatalogFilesFromStorage(String study, List<File> files, String sessionId, QueryOptions fileQueryOptions)
            throws CatalogException, StorageEngineException {

        boolean modified = synchronizeCatalogFilesFromStorage(study, files, sessionId);
        if (modified) {
            // Files updated. Reload files from catalog
            for (int i = 0; i < files.size(); i++) {
                File file = files.get(i);
                files.set(i, catalogManager.getFileManager().get(study, file.getId(), fileQueryOptions, sessionId).first());
            }
        }
        return modified;
    }

    /**
     * Updates catalog metadata from storage metadata.
     *
     * @param studyFqn              Study FQN
     * @param files                 Files to update
     * @param sessionId             User session id
     * @return if there were modifications in catalog
     * @throws CatalogException     if there is an error with catalog
     */
    public boolean synchronizeCatalogFilesFromStorage(String studyFqn, List<File> files, String sessionId)
            throws CatalogException {
        StudyMetadata study = metadataManager.getStudyMetadata(studyFqn);
        if (study == null) {
            return false;
        }
        // Update Catalog file and cohort status.
        logger.info("Synchronizing study " + study.getName());

        if (files != null && files.isEmpty()) {
            files = null;
        }

        return synchronizeFiles(study, files, sessionId);
    }

    public boolean synchronizeCatalogStudyFromStorage(String study, String sessionId)
            throws CatalogException {
        StudyMetadata studyMetadata = metadataManager.getStudyMetadata(study);
        if (studyMetadata != null) {
            // Update Catalog file and cohort status.
            return synchronizeCatalogStudyFromStorage(studyMetadata, sessionId);
        } else {
            return false;
        }
    }

    /**
     * Updates catalog metadata from storage metadata.
     *
     * 1) Update cohort ALL
     * 2) Update cohort status (calculating / invalid)
     * 3) Update file status
     *
     *
     * @param study                 StudyMetadata
     * @param sessionId             User session id
     * @return if there were modifications in catalog
     * @throws CatalogException     if there is an error with catalog
     */
    public boolean synchronizeCatalogStudyFromStorage(StudyMetadata study, String sessionId)
            throws CatalogException {
        logger.info("Synchronizing study " + study.getName());

        boolean modified = synchronizeFiles(study, null, sessionId);

        modified |= synchronizeCohorts(study, sessionId);

        return modified;
    }

    public boolean synchronizeCohorts(String study, String sessionId) throws CatalogException {
        StudyMetadata studyMetadata = getStudyMetadata(study);
        if (studyMetadata == null) {
            return false;
        } else {
            return synchronizeCohorts(studyMetadata, sessionId);
        }
    }

    protected boolean synchronizeCohorts(StudyMetadata study, String sessionId) throws CatalogException {
        boolean modified = false;
        Map<Integer, String> sampleNameMap = new HashMap<>();
        metadataManager.sampleMetadataIterator(study.getId()).forEachRemaining(sampleMetadata -> {
            sampleNameMap.put(sampleMetadata.getId(), sampleMetadata.getName());
        });

        //Check if cohort ALL has been modified
        String defaultCohortName = StudyEntry.DEFAULT_COHORT;
        CohortMetadata defaultCohortStorage = metadataManager.getCohortMetadata(study.getId(), defaultCohortName);
        if (defaultCohortStorage != null) {
            Set<String> cohortFromStorage = defaultCohortStorage.getSamples()
                    .stream()
                    .map(sampleNameMap::get)
                    .collect(Collectors.toSet());
            Cohort defaultCohort = catalogManager.getCohortManager()
                    .get(study.getName(), defaultCohortName, COHORT_QUERY_OPTIONS, sessionId).first();
            List<String> cohortFromCatalog = defaultCohort
                    .getSamples()
                    .stream()
                    .map(Sample::getId)
                    .collect(Collectors.toList());

            if (cohortFromCatalog.size() != cohortFromStorage.size() || !cohortFromStorage.containsAll(cohortFromCatalog)) {
                if (defaultCohort.getInternal().getStatus().getName().equals(CohortStatus.CALCULATING)) {
                    String status;
                    if (defaultCohortStorage.isInvalid()) {
                        status = CohortStatus.INVALID;
                    } else if (defaultCohortStorage.isStatsReady()) {
                        status = CohortStatus.READY;
                    } else {
                        status = CohortStatus.NONE;
                    }
                    catalogManager.getCohortManager().setStatus(study.getName(), defaultCohortName, status, null, sessionId);
                }
                logger.info("Update cohort " + defaultCohortName);
                QueryOptions options = new QueryOptions(Constants.ACTIONS, new ObjectMap(CohortDBAdaptor.QueryParams.SAMPLES.key(),
                        ParamUtils.BasicUpdateAction.SET));
                List<SampleReferenceParam> samples = cohortFromStorage.stream().map(s -> new SampleReferenceParam().setId(s))
                        .collect(Collectors.toList());
                catalogManager.getCohortManager().update(study.getName(), defaultCohortName,
                        new CohortUpdateParams().setSamples(samples),
                        true, options, sessionId);
                modified = true;
            }
        } else {
            logger.info("Cohort " + defaultCohortName + " not found in variant storage");
        }

        Map<String, CohortMetadata> calculatedStats = new HashMap<>();
        for (CohortMetadata cohortMetadata : metadataManager.getCalculatedCohorts(study.getId())) {
            calculatedStats.put(cohortMetadata.getName(), cohortMetadata);
        }
        //Check if any cohort stat has been updated
        if (!calculatedStats.isEmpty()) {
            Query query = new Query(CohortDBAdaptor.QueryParams.ID.key(), calculatedStats.keySet());

            try (DBIterator<Cohort> iterator = catalogManager.getCohortManager().iterator(study.getName(),
                    query, COHORT_QUERY_OPTIONS, sessionId)) {
                while (iterator.hasNext()) {
                    Cohort cohort = iterator.next();
                    CohortMetadata storageCohort = calculatedStats.get(cohort.getId());
                    if (cohort.getInternal().getStatus() != null && cohort.getInternal().getStatus().getName().equals(CohortStatus.INVALID)) {
                        if (cohort.getSamples().size() != storageCohort.getSamples().size()) {
                            // Skip this cohort. This cohort should remain as invalid
                            logger.debug("Skip " + cohort.getId());
                            continue;
                        }

                        Set<String> cohortFromCatalog = cohort.getSamples()
                                .stream()
                                .map(Sample::getId)
                                .collect(Collectors.toSet());
                        Set<String> cohortFromStorage = storageCohort.getSamples()
                                .stream()
//                                .map(s -> metadataManager.getSampleName(study.getId(), s))
                                .map(sampleNameMap::get)
                                .collect(Collectors.toSet());
                        if (!cohortFromCatalog.equals(cohortFromStorage)) {
                            // Skip this cohort. This cohort should remain as invalid
                            logger.debug("Skip " + cohort.getId());
                            continue;
                        }
                    }
                    if (cohort.getInternal().getStatus() == null || !cohort.getInternal().getStatus().getName().equals(CohortStatus.READY)) {
                        logger.debug("Cohort \"{}\" change status to {}", cohort.getId(), CohortStatus.READY);
                        catalogManager.getCohortManager().setStatus(study.getName(), cohort.getId(), CohortStatus.READY,
                                "Update status from Storage", sessionId);
                        modified = true;
                    }
                }
            }
        }

        Map<String, CohortMetadata> invalidStats = new HashMap<>();
        for (CohortMetadata cohortMetadata : metadataManager.getInvalidCohorts(study.getId())) {
            invalidStats.put(cohortMetadata.getName(), cohortMetadata);
        }
        //Check if any cohort stat has been invalidated
        if (!invalidStats.isEmpty()) {
            Query query = new Query(CohortDBAdaptor.QueryParams.ID.key(), invalidStats.keySet());
            try (DBIterator<Cohort> iterator = catalogManager.getCohortManager().iterator(study.getName(),
                    query,
                    COHORT_QUERY_OPTIONS, sessionId)) {
                while (iterator.hasNext()) {
                    Cohort cohort = iterator.next();
                    if (cohort.getInternal().getStatus() == null || !cohort.getInternal().getStatus().getName().equals(CohortStatus.INVALID)) {
                        logger.debug("Cohort \"{}\" change status to {}", cohort.getId(), CohortStatus.INVALID);
                        catalogManager.getCohortManager().setStatus(study.getName(), cohort.getId(), CohortStatus.INVALID,
                                "Update status from Storage", sessionId);
                        modified = true;
                    }
                }
            }
        }
        return modified;
    }

    protected boolean synchronizeFiles(StudyMetadata study, List<File> files, String sessionId) throws CatalogException {
        boolean modified = false;
        BiMap<Integer, String> fileNameMap = HashBiMap.create();
        Map<Integer, String> filePathMap = new HashMap<>();
        Map<String, Set<String>> fileSamplesMap = new HashMap<>();
        LinkedHashSet<Integer> indexedFilesFromStorage;
        if (CollectionUtils.isEmpty(files)) {
            metadataManager.fileMetadataIterator(study.getId()).forEachRemaining(fileMetadata -> {
                fileNameMap.put(fileMetadata.getId(), fileMetadata.getName());
                filePathMap.put(fileMetadata.getId(), fileMetadata.getPath());
                Set<String> samples;
                if (!fileMetadata.isIndexed()) {
                    // Skip non indexed files
                    return;
                }
                if (fileMetadata.getSamples() == null) {
                    logger.warn("File '{}' with null samples", fileMetadata.getName());
                    try {
                        VariantFileMetadata variantFileMetadata =
                                metadataManager.getVariantFileMetadata(study.getId(), fileMetadata.getId(), new QueryOptions()).first();
                        if (variantFileMetadata == null) {
                            logger.error("Missing VariantFileMetadata from file {}", fileMetadata.getName());
                        } else {
                            logger.info("Samples from VariantFileMetadata: {}", variantFileMetadata.getSampleIds());
                        }
                    } catch (StorageEngineException e) {
                        logger.error("Error reading VariantFileMetadata for file " + fileMetadata.getName(), e);
                    }
                    samples = Collections.emptySet();
                } else {
                    if (fileMetadata.getSamples().contains(null)) {
                        logger.warn("File '{}' has a null sampleId in samples", fileMetadata.getName());
                    }
                    samples = fileMetadata.getSamples()
                            .stream()
                            .map(s -> metadataManager.getSampleName(study.getId(), s))
                            .collect(Collectors.toSet());
                }
                fileSamplesMap.put(fileMetadata.getName(), samples);
            });
            indexedFilesFromStorage = metadataManager.getIndexedFiles(study.getId());
        } else {
            indexedFilesFromStorage = new LinkedHashSet<>();
            for (File file : files) {
                FileMetadata fileMetadata = metadataManager.getFileMetadata(study.getId(), file.getName());
                if (fileMetadata != null) {
                    fileNameMap.put(fileMetadata.getId(), fileMetadata.getName());
                    filePathMap.put(fileMetadata.getId(), fileMetadata.getPath());
                    if (fileMetadata.isIndexed()) {
                        indexedFilesFromStorage.add(fileMetadata.getId());
                    }
                }
            }
        }

        if (!indexedFilesFromStorage.isEmpty()) {
            List<String> indexedFilesUris = new ArrayList<>();
            for (Integer fileId : indexedFilesFromStorage) {
                String path = filePathMap.get(fileId);
                indexedFilesUris.add(toUri(path));
            }
            int numFiles = 0;
            logger.info("Synchronize {} files", indexedFilesUris.size());
            while (!indexedFilesUris.isEmpty()) {
                int numPendingFiles = indexedFilesUris.size();
                List<String> indexedFilesUrisSubset = indexedFilesUris.subList(0, Math.min(2000, indexedFilesUris.size()));
                logger.info("Synchronize {}/{} files", indexedFilesUrisSubset.size(), indexedFilesUris.size());
                Query query = new Query(FileDBAdaptor.QueryParams.URI.key(), indexedFilesUrisSubset);
                try (DBIterator<File> iterator = catalogManager.getFileManager()
                        .iterator(study.getName(), query, INDEXED_FILES_QUERY_OPTIONS, sessionId)) {
                    while (iterator.hasNext()) {
                        File file = iterator.next();
                        modified = synchronizeIndexedFile(study, sessionId, modified, fileSamplesMap, file);

                        // Remove processed file from list of uris
                        indexedFilesUrisSubset.remove(file.getUri().toString());
                        numFiles++;
                    }
                } catch (MongoServerException e) {
                    if (numPendingFiles == indexedFilesUris.size()) {
                        // No files where processed in this loop. Do not continue.
                        throw e;
                    }
                    logger.warn("Catch exception " + e.toString() + ". Continue");
                }
                if (!indexedFilesUrisSubset.isEmpty()) {
                    logger.warn("Unable to find {} files in catalog: {}", indexedFilesUrisSubset.size(), indexedFilesUrisSubset);
                    // Discard not found files
                    indexedFilesUrisSubset.clear();
                }
            }
            if (numFiles != indexedFilesFromStorage.size()) {
                logger.warn("{} out of {} files were not found in catalog given their file uri",
                        indexedFilesFromStorage.size() - numFiles,
                        indexedFilesFromStorage.size());
            }
        }

        // Update READY files
        Query indexedFilesQuery;
        if (CollectionUtils.isEmpty(files)) {
            indexedFilesQuery = INDEXED_FILES_QUERY;
        } else {
            List<String> catalogFileIds = files.stream()
                    .map(File::getId)
                    .collect(Collectors.toList());
            indexedFilesQuery = new Query(INDEXED_FILES_QUERY).append(ID.key(), catalogFileIds);
        }
        try (DBIterator<File> iterator = catalogManager.getFileManager()
                .iterator(study.getName(), indexedFilesQuery, INDEXED_FILES_QUERY_OPTIONS, sessionId)) {
            while (iterator.hasNext()) {
                File file = iterator.next();
                Integer fileId = fileNameMap.inverse().get(file.getName());
                if (fileId == null || !indexedFilesFromStorage.contains(fileId)) {
                    String newStatus;
                    if (hasTransformedFile(file.getInternal().getIndex())) {
                        newStatus = IndexStatus.TRANSFORMED;
                    } else {
                        newStatus = IndexStatus.NONE;
                    }
                    logger.info("File \"{}\" change status from {} to {}", file.getName(),
                            IndexStatus.READY, newStatus);
                    catalogManager.getFileManager()
                            .updateFileIndexStatus(file, newStatus, "Not indexed, regarding Storage Metadata", sessionId);
                    modified = true;
                }
            }
        }

        Set<String> loadingFilesRegardingCatalog = new HashSet<>();

        // Update ongoing files
        Query runningIndexFilesQuery;
        if (CollectionUtils.isEmpty(files)) {
            runningIndexFilesQuery = RUNNING_INDEX_FILES_QUERY;
        } else {
            List<String> catalogFileIds = files.stream()
                    .map(File::getId)
                    .collect(Collectors.toList());
            runningIndexFilesQuery = new Query(RUNNING_INDEX_FILES_QUERY).append(ID.key(), catalogFileIds);
        }
        try (DBIterator<File> iterator = catalogManager.getFileManager()
                .iterator(study.getName(), runningIndexFilesQuery, INDEXED_FILES_QUERY_OPTIONS, sessionId)) {
            while (iterator.hasNext()) {
                File file = iterator.next();
                Integer fileId = fileNameMap.inverse().get(file.getName());
                FileMetadata fileMetadata;
                if (fileId == null) {
                    fileMetadata = null;
                } else {
                    fileMetadata = metadataManager.getFileMetadata(study.getId(), fileId);
                }

                // If last LOAD operation is ERROR or there is no LOAD operation
                if (fileMetadata != null && fileMetadata.getIndexStatus().equals(TaskMetadata.Status.ERROR)) {
                    OpenCGAResult<Job> jobsFromFile = catalogManager
                            .getJobManager()
                            .search(study.getName(),
                                    new Query()
                                            .append(JobDBAdaptor.QueryParams.INPUT.key(), file.getId())
                                            .append(JobDBAdaptor.QueryParams.TOOL_ID.key(), VariantIndexOperationTool.ID)
                                            .append(JobDBAdaptor.QueryParams.INTERNAL_STATUS_NAME.key(), Enums.ExecutionStatus.RUNNING),
                                    new QueryOptions(QueryOptions.INCLUDE, JobDBAdaptor.QueryParams.ID.key()),
                                    sessionId);
                    if (jobsFromFile.getResults().isEmpty()) {
                        final FileIndex index;
                        index = file.getInternal().getIndex() == null ? new FileIndex() : file.getInternal().getIndex();
                        String prevStatus = index.getStatus().getName();
                        String newStatus;
                        if (hasTransformedFile(index)) {
                            newStatus = IndexStatus.TRANSFORMED;
                        } else {
                            newStatus = IndexStatus.NONE;
                        }
                        logger.info("File \"{}\" change status from {} to {}", file.getName(),
                                prevStatus, newStatus);
                        catalogManager.getFileManager().updateFileIndexStatus(file, newStatus,
                                "Error loading. Reset status to " + newStatus,
                                sessionId);
                        modified = true;
                    } else {
                        // Running job. Might be transforming, or have just started. Do not modify the status!
                        loadingFilesRegardingCatalog.add(file.getName());
                    }
                } else {
                    loadingFilesRegardingCatalog.add(file.getName());
                }
            }
        }

        // Update running LOAD operations, regarding storage
        Set<String> loadingFilesRegardingStorage = new HashSet<>();
        for (TaskMetadata runningTask : metadataManager.getRunningTasks(study.getId())) {
            if (runningTask.getType().equals(TaskMetadata.Type.LOAD)
                    && runningTask.currentStatus() != null
                    && runningTask.currentStatus().equals(TaskMetadata.Status.RUNNING)) {
                for (Integer fileId : runningTask.getFileIds()) {
                    String filePath = filePathMap.get(fileId);
                    if (filePath != null) {
                        loadingFilesRegardingStorage.add(toUri(filePath));
                    }
                }
            }
        }

        if (!loadingFilesRegardingStorage.isEmpty()) {
            try (DBIterator<File> iterator = catalogManager.getFileManager()
                    .iterator(study.getName(), new Query(URI.key(), loadingFilesRegardingStorage),
                            INDEXED_FILES_QUERY_OPTIONS, sessionId)) {
                while (iterator.hasNext()) {
                    File file = iterator.next();
                    String newStatus;
                    if (hasTransformedFile(file.getInternal().getIndex())) {
                        newStatus = IndexStatus.LOADING;
                    } else {
                        newStatus = IndexStatus.INDEXING;
                    }
                    catalogManager.getFileManager().updateFileIndexStatus(file, newStatus,
                            "File is being loaded regarding Storage", sessionId);
                    modified = true;
                }
            }
        }
        return modified;
    }

    private boolean synchronizeIndexedFile(StudyMetadata study, String sessionId, boolean modified, Map<String, Set<String>> fileSamplesMap, File file) throws CatalogException {
        String status = file.getInternal().getIndex() == null || file.getInternal().getIndex().getStatus() == null
                ? IndexStatus.NONE
                : file.getInternal().getIndex().getStatus().getName();
        if (!status.equals(IndexStatus.READY)) {
            final FileIndex index;
            index = file.getInternal().getIndex() == null ? new FileIndex() : file.getInternal().getIndex();
            if (index.getStatus() == null) {
                index.setStatus(new IndexStatus());
            }
            logger.debug("File \"{}\" change status from {} to {}", file.getName(), status, IndexStatus.READY);
            index.getStatus().setName(IndexStatus.READY);
            catalogManager.getFileManager()
                    .updateFileIndexStatus(file, IndexStatus.READY, "Indexed, regarding Storage Metadata", sessionId);
            modified = true;
        }
        Set<String> storageSamples = fileSamplesMap.get(file.getName());
        Set<String> catalogSamples = new HashSet<>(file.getSampleIds());
        if (storageSamples == null) {
            storageSamples = new HashSet<>();
            Integer fileId = metadataManager.getFileId(study.getId(), file.getName());
            for (Integer sampleId : metadataManager.getSampleIdsFromFileId(study.getId(), fileId)) {
                storageSamples.add(metadataManager.getSampleName(study.getId(), sampleId));
            }
        }
        if (!storageSamples.equals(catalogSamples)) {
            logger.warn("File samples does not match between catalog and storage for file '{}'. "
                    + "Update catalog variant file metadata", file.getPath());
            file = catalogManager.getFileManager().get(study.getName(), file.getId(), new QueryOptions(), sessionId).first();
            new FileMetadataReader(catalogManager).updateMetadataInformation(study.getName(), file, sessionId);
        }
        return modified;
    }

    private String toUri(String path) {
        String uri;
        if (path.startsWith("/")) {
            uri = "file://" + path;
        } else {
            uri = Paths.get(path).toUri().toString();
        }
        return uri;
    }

    public boolean hasTransformedFile(FileIndex index) {
        return index.getTransformedFile() != null && index.getTransformedFile().getId() > 0;
    }

    public void synchronizeRemovedStudyFromStorage(String study, String token) throws CatalogException {
        catalogManager.getCohortManager().update(study, StudyEntry.DEFAULT_COHORT,
                new CohortUpdateParams().setSamples(Collections.emptyList()),
                true,
                new QueryOptions(Constants.ACTIONS, new ObjectMap(CohortDBAdaptor.QueryParams.SAMPLES.key(), "SET")),
                token);

        catalogManager.getCohortManager().setStatus(study, StudyEntry.DEFAULT_COHORT, CohortStatus.NONE,
                "Study has been removed from storage", token);


        String userId = catalogManager.getUserManager().getUserId(token);
        try (DBIterator<File> iterator = catalogManager.getFileManager()
                .iterator(study, INDEXED_FILES_QUERY, INDEXED_FILES_QUERY_OPTIONS, token)) {
            while (iterator.hasNext()) {
                File file = iterator.next();
                catalogManager.getFileManager().setFileIndex(study, file.getId(),
                        new FileIndex(userId, TimeUtils.getTime(), new IndexStatus(IndexStatus.NONE), -1, null, null, null), token);
            }
        }
    }
}
