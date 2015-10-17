/*
 *  The MIT License
 *
 *   Copyright (c) 2015, Mahmoud Ben Hassine (mahmoud@benhassine.fr)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *   THE SOFTWARE.
 */

package org.easybatch.jdbc;

import org.easybatch.core.api.Report;
import org.easybatch.core.reader.IterableMultiRecordReader;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easybatch.core.impl.EngineBuilder.aNewEngine;

public class JdbcMultiRecordWriterTest {

    private static final String DATABASE_URL = "jdbc:hsqldb:mem";
    private static final String USER = "sa";
    private static final String PASSWORD = "pwd";

    private static Connection connection;

    private JdbcMultiRecordWriter jdbcMultiRecordWriter;

    @BeforeClass
    public static void initDatabase() throws Exception {
        System.setProperty("hsqldb.reconfig_logging", "false");
        connection = DriverManager.getConnection(DATABASE_URL, "sa", "pwd");
        connection.setAutoCommit(false);
        createTweetTable(connection);
    }

    @Before
    public void setUp() throws Exception {
        String query = "INSERT INTO tweet VALUES (?,?,?);";
        PreparedStatementProvider preparedStatementProvider = new PreparedStatementProvider() {
            @Override
            public void prepareStatement(PreparedStatement statement, Object record) throws SQLException {
                Tweet tweet = (Tweet) record;
                statement.setInt(1, tweet.getId());
                statement.setString(2, tweet.getUser());
                statement.setString(3, tweet.getMessage());
            }
        };
        jdbcMultiRecordWriter = new JdbcMultiRecordWriter(connection, query, preparedStatementProvider);
    }

    @Test
    public void testRecordWritingInChunks() throws Exception {

        Integer nbTweetsToInsert = 13;
        Integer chunkSize = 5;

        List<Tweet> tweets = createTweets(nbTweetsToInsert);

        Report report = aNewEngine()
                .reader(new IterableMultiRecordReader<Tweet>(tweets, chunkSize))
                .writer(jdbcMultiRecordWriter)
                .pipelineEventListener(new JdbcTransactionPipelineListener(connection)) // needed since autocommit = false
                .jobEventListener(new JdbcConnectionJobListener(connection))
                .call();

        assertThat(report).isNotNull();
        assertThat(report.getTotalRecords()).isEqualTo(3);// 3 multi-records
        assertThat(report.getSuccessRecordsCount()).isEqualTo(3);// 3 multi-records

        int nbTweetsInDatabase = countTweetsInDatabase();

        assertThat(nbTweetsInDatabase).isEqualTo(nbTweetsToInsert);
    }

    private int countTweetsInDatabase() throws SQLException {
        Connection connection = getConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("select * from tweet");
        int nbTweets = 0;
        while (resultSet.next()) {
            nbTweets++;
        }
        resultSet.close();
        statement.close();
        connection.close();
        return nbTweets;
    }

    private List<Tweet> createTweets(Integer nbTweetsToInsert) {
        List<Tweet> tweets = new ArrayList<Tweet>();
        for (int i = 1; i <= nbTweetsToInsert; i++) {
            tweets.add(new Tweet(i, "user " + i, "hello " + i));
        }
        return tweets;
    }

    @AfterClass
    public static void shutdownDatabase() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        //delete hsqldb tmp files
        new File("mem.log").delete();
        new File("mem.properties").delete();
        new File("mem.script").delete();
        new File("mem.tmp").delete();
        new File("mem.lck").delete();
    }

    private static void createTweetTable(Connection connection) throws Exception {
        Statement statement = connection.createStatement();
        String query = "DROP TABLE IF EXISTS tweet";
        statement.executeUpdate(query);
        query = "CREATE TABLE tweet (\n" +
                "  id integer NOT NULL PRIMARY KEY,\n" +
                "  user varchar(32) NOT NULL,\n" +
                "  message varchar(140) NOT NULL,\n" +
                ");";
        statement.executeUpdate(query);
        statement.close();
        connection.commit();
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL, USER, PASSWORD);
    }

}