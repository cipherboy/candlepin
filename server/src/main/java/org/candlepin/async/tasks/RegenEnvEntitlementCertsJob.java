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
import org.candlepin.async.ArgumentConversionException;
import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * RegenEnvEntitlementCertsJob
 *
 * Regenerates entitlements within an environment which are affected by the
 * promotion/demotion of the given content sets.
 */
public class RegenEnvEntitlementCertsJob implements AsyncJob {

    public static final String JOB_KEY = "REGEN_ENV_ENT_CERTS_JOB";
    public static final String JOB_NAME = "regen_env_entitlement_certs";

    private static final String ENV_ID = "env_id";
    private static final String CONTENT = "content_ids";
    private static final String LAZY_REGEN = "lazy_regen";

    private PoolManager poolManager;

    @Inject
    public RegenEnvEntitlementCertsJob(PoolManager poolManager) {
        this.poolManager = poolManager;
    }

    @Override
    public Object execute(JobExecutionContext context) {
        JobArguments args = context.getJobArguments();

        String environmentId = args.getAsString(ENV_ID);
        Set<String> contentIds = new HashSet<>(Arrays.asList(args.getAs(CONTENT, String[].class)));
        Boolean lazy = args.getAsBoolean(LAZY_REGEN, true);

        this.poolManager.regenerateCertificatesOf(environmentId, contentIds, lazy);

        return "Successfully regenerated entitlements for environment " + environmentId;
    }

    /**
     * Job configuration object for the regenerate environment entitlements job
     */
    public static class RegenEnvEntitlementCertsJobConfig extends JobConfig {
        private RegenEnvEntitlementCertsJobConfig() {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME);
        }

        /**
         * Sets the owner for this regenerate environment entitlements job.
         * The owner is not required, but provides the org context in which the job will be executed.
         *
         * @param owner
         *  the owner to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public RegenEnvEntitlementCertsJobConfig setOwner(Owner owner) {
            if (owner == null) {
                throw new IllegalArgumentException("owner is null");
            }

            this.setJobMetadata(LoggingFilter.OWNER_KEY, owner.getKey())
                .setLogLevel(owner.getLogLevel());

            return this;
        }

        /**
         * Sets the environment for this regenerate environment entitlements job.
         * The environment is required by this job.
         *
         * @param environment
         *  the environment to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public RegenEnvEntitlementCertsJobConfig setEnvironment(Environment environment) {
            if (environment == null || environment.getId() == null) {
                throw new IllegalArgumentException("environment is null or has null id");
            }

            this.setJobArgument(ENV_ID, environment.getId());
            return this;
        }

        /**
         * Sets the content ids for this regenerate environment entitlements job.
         * The content ids are required by this job.
         *
         * @param contentIds
         *  the collection of content ids to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public RegenEnvEntitlementCertsJobConfig setContent(Set<String> contentIds) {
            this.setJobArgument(CONTENT, contentIds.toArray());
            return this;
        }

        /**
         * Sets whether or not to generate the certificates immediately, or mark them as dirty and allow
         * them to be regenerated on-demand.
         *
         * @param lazy
         *  whether or not to generate the certificates immediately
         *
         * @return
         *  a reference to this job config
         */
        public RegenEnvEntitlementCertsJobConfig setLazyRegeneration(boolean lazy) {
            this.setJobArgument(LAZY_REGEN, lazy);
            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                JobArguments arguments = this.getJobArguments();

                String environmentId = arguments.getAsString(ENV_ID);
                String[] contentIds = arguments.getAs(CONTENT, String[].class);

                if (environmentId == null || environmentId.isEmpty()) {
                    String errmsg = "environment has not been set, or the provided environment lacks an id";
                    throw new JobConfigValidationException(errmsg);
                }

                if (contentIds == null || contentIds.length == 0) {
                    String errmsg = "content ids have not been set";
                    throw new JobConfigValidationException(errmsg);
                }
            }
            catch (ArgumentConversionException e) {
                String errmsg = "One or more required arguments are of the wrong type";
                throw new JobConfigValidationException(errmsg, e);
            }
        }
    }

    /**
     * Creates a JobConfig configured to execute the regenerate environment entitlement certs job.
     * Callers may further manipulate the JobConfig as necessary before queuing it.
     *
     * @return
     *  a JobConfig instance configured to execute the regenerate environment entitlement certs job
     */
    public static RegenEnvEntitlementCertsJobConfig createJobConfig() {
        return new RegenEnvEntitlementCertsJobConfig();
    }
}
