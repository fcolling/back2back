package org.ogerardin.b2b.batch.mongodb;


import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.repository.dao.JobInstanceDao;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static com.mongodb.BasicDBObjectBuilder.start;

/**
 * Uses MongoTemplate to perform CRUD on Springbatch's Job Instance Data to
 * Mongo DB. <br/>MongoTemplate needs to be set as a property during bean definition
 * 
 * @author Baruch S.
 * @authoer vfouzdar
 */
@Repository
public class MongoJobInstanceDao extends AbstractMongoDao implements JobInstanceDao {

	 private MongoTemplate mongoTemplate;
	 
	 public void setMongoTemplate(MongoTemplate mongoTemplate){
		this.mongoTemplate = mongoTemplate; 
	 }
	 
	 
    @PostConstruct
    public void init() {
        getCollection(). createIndex(jobInstanceIdObj(1L));
    }

    public JobInstance createJobInstance(String jobName, final JobParameters jobParameters) {
        Assert.notNull(jobName, "Job name must not be null.");
        Assert.notNull(jobParameters, "JobParameters must not be null.");

        Assert.state(getJobInstance(jobName, jobParameters) == null,
                "JobInstance must not already exist");

        Long jobId = getNextId(JobInstance.class.getSimpleName(), mongoTemplate);

        JobInstance jobInstance = new JobInstance(jobId, jobName);

        jobInstance.incrementVersion();

        Map<String, JobParameter> jobParams = jobParameters.getParameters();
        Map<String, Object> paramMap = new HashMap<String, Object>(jobParams.size());
        for (Map.Entry<String, JobParameter> entry : jobParams.entrySet()) {
            paramMap.put(entry.getKey().replaceAll(DOT_STRING, DOT_ESCAPE_STRING), entry.getValue().getValue());
        }
        getCollection().save(start()
                .add(JOB_INSTANCE_ID_KEY, jobId)
                .add(JOB_NAME_KEY, jobName)
                .add(JOB_KEY_KEY, createJobKey(jobParameters))
                .add(VERSION_KEY, jobInstance.getVersion())
                .add(JOB_PARAMETERS_KEY, new BasicDBObject(paramMap)).get());
        return jobInstance;
    }

    public JobInstance getJobInstance(String jobName, JobParameters jobParameters) {
        Assert.notNull(jobName, "Job name must not be null.");
        Assert.notNull(jobParameters, "JobParameters must not be null.");

        String jobKey = createJobKey(jobParameters);

        return mapJobInstance(getCollection().findOne(start()
                .add(JOB_NAME_KEY, jobName)
                .add(JOB_KEY_KEY, jobKey).get()), jobParameters);
    }

    public JobInstance getJobInstance(Long instanceId) {
        return mapJobInstance(getCollection().findOne(jobInstanceIdObj(instanceId)));
    }

    public JobInstance getJobInstance(JobExecution jobExecution) {
        DBObject instanceId = mongoTemplate.getCollection(JobExecution.class.getSimpleName()).findOne(jobExecutionIdObj(jobExecution.getId()), jobInstanceIdObj(1L));
        removeSystemFields(instanceId);
        return mapJobInstance(getCollection().findOne(instanceId));
    }

    public List<JobInstance> getJobInstances(String jobName, int start, int count) {
        return mapJobInstances(getCollection().find(new BasicDBObject(JOB_NAME_KEY, jobName)).sort(jobInstanceIdObj(-1L)).skip(start).limit(count));
    }

    @SuppressWarnings({"unchecked"})
    public List<String> getJobNames() {
        List results = getCollection().distinct(JOB_NAME_KEY);
        Collections.sort(results);
        return results;
    }

    protected String createJobKey(JobParameters jobParameters) {

        Map<String, JobParameter> props = jobParameters.getParameters();
        StringBuilder stringBuilder = new StringBuilder();
        List<String> keys = new ArrayList<String>(props.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            stringBuilder.append(key).append("=").append(props.get(key).toString()).append(";");
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "MD5 algorithm not available.  Fatal (should be in the JDK).");
        }

        try {
            byte[] bytes = digest.digest(stringBuilder.toString().getBytes(
                    "UTF-8"));
            return String.format("%032x", new BigInteger(1, bytes));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(
                    "UTF-8 encoding not available.  Fatal (should be in the JDK).");
        }
    }

    @Override
    protected DBCollection getCollection() {
        return mongoTemplate.getCollection(JobInstance.class.getSimpleName());
    }

    private List<JobInstance> mapJobInstances(DBCursor dbCursor) {
        List<JobInstance> results = new ArrayList<JobInstance>();
        while (dbCursor.hasNext()) {
            results.add(mapJobInstance(dbCursor.next()));
        }
        return results;
    }

    private JobInstance mapJobInstance(DBObject dbObject) {
        return mapJobInstance(dbObject, null);
    }

    private JobInstance mapJobInstance(DBObject dbObject, JobParameters jobParameters) {
        JobInstance jobInstance = null;
        if (dbObject != null) {
            Long id = (Long) dbObject.get(JOB_INSTANCE_ID_KEY);
            if (jobParameters == null) {
                jobParameters = getJobParameters(id, mongoTemplate);
            }
            
            jobInstance = new JobInstance(id, (String) dbObject.get(JOB_NAME_KEY)); // should always be at version=0 because they never get updated
            jobInstance.incrementVersion();
        }
        return jobInstance;
    }


	@Override
	public List<JobInstance> findJobInstancesByName(String jobName, int start,
                                                    int count) {
		List<JobInstance> result = new ArrayList<JobInstance>();
		List<JobInstance> jobInstances = mapJobInstances(getCollection().find(
				new BasicDBObject(JOB_NAME_KEY, jobName)).sort(
				jobInstanceIdObj(-1L)));
		for (JobInstance instanceEntry : jobInstances) {
			String key = instanceEntry.getJobName();
			String curJobName = key.substring(0, key.lastIndexOf("|"));

			if(curJobName.equals(jobName)) {
				result.add(instanceEntry);
			}
		}
		return result;
	}

	@Override
	public int getJobInstanceCount(String jobName) throws NoSuchJobException {

		int count = 0;
		List<JobInstance> jobInstances = mapJobInstances(getCollection().find(
				new BasicDBObject(JOB_NAME_KEY, jobName)).sort(
				jobInstanceIdObj(-1L)));
		for (JobInstance instanceEntry : jobInstances) {
			String key = instanceEntry.getJobName();
			String curJobName = key.substring(0, key.lastIndexOf("|"));

			if(curJobName.equals(jobName)) {
				count++;
			}
		}

		if(count == 0) {
			throw new NoSuchJobException("No job instances for job name " + jobName + " were found");
		} else {
			return count;
		}
	}

}
