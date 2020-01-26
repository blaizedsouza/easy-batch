/*
 * The MIT License
 *
 *  Copyright (c) 2020, Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package org.jeasy.batch.tutorials.advanced.scanning;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.sql.DataSource;

import org.jeasy.batch.core.job.Job;
import org.jeasy.batch.core.job.JobExecutor;
import org.jeasy.batch.core.job.JobReport;
import org.jeasy.batch.core.record.Header;
import org.jeasy.batch.flatfile.DelimitedRecordMapper;
import org.jeasy.batch.flatfile.FlatFileRecordReader;
import org.jeasy.batch.jdbc.BeanPropertiesPreparedStatementProvider;
import org.jeasy.batch.jdbc.JdbcRecordWriter;
import org.jeasy.batch.tutorials.common.DatabaseUtil;
import org.jeasy.batch.tutorials.common.Tweet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jeasy.batch.core.job.JobBuilder.aNewJob;

/**
 * Main class to run the batch scanning tutorial.
 *
 * The batch scanning feature allows you to scan a batch for the faulty records
 * when an exception occurs during batch writing. Each record will be re-processed
 * alone as a singleton batch by the writer. Scanned records will be flagged using
 * the {@link Header#isScanned()} boolean to be able to identify them in listeners.
 *
 * Note: This feature works well with transactional writers where a failed write
 * operation can be re-executed without side effects. However, a known limitation
 * is that when used with a non-transactional writer, items might be written twice
 * (like in the case of a file writer where the output stream is flushed before the
 * exception occurs).
 *
 * The goal is to read tweets from a CSV file and load them in a relational database.
 *
 * @author Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 */
public class Launcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(Launcher.class);

    public static void main(String[] args) throws Exception {
        // Start embedded database server
        DatabaseUtil.startEmbeddedDatabase();
        DatabaseUtil.deleteAllTweets();

        DataSource dataSource = DatabaseUtil.getDataSource();
        String query = "INSERT INTO tweet VALUES (?,?,?);";
        String[] fields = {"id", "user", "message"};

        // Build a batch job
        Path tweets = Paths.get(args.length != 0 ? args[0] : "easy-batch-tutorials/src/main/resources/data/tweets-with-invalid-records.csv");
        Path skippedTweets = Paths.get(args.length != 0 ? args[0] : "easy-batch-tutorials/target/skipped-tweets.csv");
        Job job = aNewJob()
                .batchSize(5)
                .enableBatchScanning(true)
                .reader(new FlatFileRecordReader(tweets))
                .mapper(new DelimitedRecordMapper<>(Tweet.class, fields))
                .writer(new JdbcRecordWriter(dataSource, query, new BeanPropertiesPreparedStatementProvider(Tweet.class, fields)))
                .writerListener(new ScannedRecordListener(skippedTweets))
                .build();
        
        // Execute the job
        JobExecutor jobExecutor = new JobExecutor();
        JobReport jobReport = jobExecutor.execute(job);
        jobExecutor.shutdown();

        System.out.println(jobReport);

        // Dump tweet table to check inserted data
        DatabaseUtil.dumpTweetTable();

        // Shutdown embedded database server and delete temporary files
        DatabaseUtil.cleanUpWorkingDirectory();

    }

}
