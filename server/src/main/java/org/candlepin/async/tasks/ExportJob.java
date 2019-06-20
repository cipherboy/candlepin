/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.async.tasks;

import com.google.inject.Inject;
import org.candlepin.async.AsyncJob;
import org.candlepin.async.ArgumentConversionException;
import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.async.JobConstraints;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.controller.ManifestManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.sync.ExportResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;



/**
 * A job that generates a compressed file representation of a Consumer. Once the job
 * has completed, its result data will contain the details of the newly created file
 * that can be used to download the file.
 *
 * @see ExportResult
 */
public class ExportJob implements AsyncJob {
    private static Logger log = LoggerFactory.getLogger(ExportJob.class);

    public static final String JOB_KEY = "EXPORT_JOB";
    public static final String JOB_NAME = "export_manifest";

    protected static final String CONSUMER_KEY = "consumer_uuid";
    protected static final String CDN_LABEL = "cdn_label";
    protected static final String WEBAPP_PREFIX = "webapp_prefix";
    protected static final String API_URL = "api_url";
    protected static final String EXTENSION_DATA = "extension_data";

    /**
     * Job configuration object for the export job
     */
    public static class ExportJobConfig extends JobConfig {
        public ExportJobConfig() {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME)
                .addConstraint(JobConstraints.uniqueByArgument(CONSUMER_KEY));
        }

        /**
         * Sets the consumer for this export job. The consumer is required, and also provides the
         * context in which the job will be executed.
         *
         * @param consumer
         *  the consumer to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ExportJobConfig setConsumer(Consumer consumer) {
            if (consumer == null) {
                throw new IllegalArgumentException("consumer is null");
            }

            this.setJobArgument(CONSUMER_KEY, consumer.getUuid());
            return this;
        }

        /**
         * Sets the owner for this export job. The owner is not required, but provides the org
         * context in which the job will be executed.
         *
         * @param owner
         *  the owner to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ExportJobConfig setOwner(Owner owner) {
            if (owner == null) {
                throw new IllegalArgumentException("owner is null");
            }

            this.setJobMetadata(LoggingFilter.OWNER_KEY, owner.getKey())
                .setLogLevel(owner.getLogLevel());

            return this;
        }

        /**
         * Sets the CDN label for this export job. The CDN label is required by the export job.
         *
         * @param label
         *  the label to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ExportJobConfig setCdnLabel(String label) {
            this.setJobArgument(CDN_LABEL, label);
            return this;
        }

        /**
         * Sets the web app prefix for this export job.
         *
         * @param prefix
         *  the web app prefix to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ExportJobConfig setWebAppPrefix(String prefix) {
            this.setJobArgument(WEBAPP_PREFIX, prefix);
            return this;
        }

        /**
         * Sets the api url for this export job.
         *
         * @param url
         *  the api url to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ExportJobConfig setApiUrl(String url) {
            this.setJobArgument(API_URL, url);
            return this;
        }

        /**
         * Sets the extension data for this export job.
         *
         * @param extData
         *  the extension data to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ExportJobConfig setExtensionData(Map<String, String> extData) {
            this.setJobArgument(EXTENSION_DATA, extData);
            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                JobArguments arguments = this.getJobArguments();

                String consumerUuid = arguments.getAsString(CONSUMER_KEY);
                String cdnLabel = arguments.getAsString(CDN_LABEL);

                if (consumerUuid == null || consumerUuid.isEmpty()) {
                    String errmsg = "consumer has not been set, or the provided consumer lacks a UUID";
                    throw new JobConfigValidationException(errmsg);
                }

                if (cdnLabel == null || cdnLabel.isEmpty()) {
                    String errmsg = "CDN label has not been set, or the provided label is empty";
                    throw new JobConfigValidationException(errmsg);
                }
            }
            catch (ArgumentConversionException e) {
                String errmsg = "One or more required arguments are of the wrong type";
                throw new JobConfigValidationException(errmsg, e);
            }
        }
    }

    private ManifestManager manifestManager;

    @Inject
    public ExportJob(ManifestManager manifestManager) {
        this.manifestManager = manifestManager;
    }

    @Override
    public Object execute(JobExecutionContext context) throws JobExecutionException {
        JobArguments args = context.getJobArguments();

        final String consumerUuid = args.getAsString(CONSUMER_KEY);
        final String cdnLabel = args.getAsString(CDN_LABEL);
        final String webAppPrefix = args.getAsString(WEBAPP_PREFIX);
        final String apiUrl = args.getAsString(API_URL);
        final Map<String, String> extensionData = (Map<String, String>) args.getAs(EXTENSION_DATA, Map.class);

        log.info("Starting async export for {}", consumerUuid);
        try {
            final ExportResult result = manifestManager.generateAndStoreManifest(
                consumerUuid, cdnLabel, webAppPrefix, apiUrl, extensionData);
            log.info("Async export complete.");
            return result;
        }
        catch (Exception e) {
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    /**
     * Creates a JobConfig configured to execute the export job. Callers may further manipulate
     * the JobConfig as necessary before queuing it.
     *
     * @return
     *  a JobConfig instance configured to execute the export job
     */
    public static ExportJobConfig createJobConfig() {
        return new ExportJobConfig();
    }
}
