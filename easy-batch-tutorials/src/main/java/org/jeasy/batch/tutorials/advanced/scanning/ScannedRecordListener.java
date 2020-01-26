package org.jeasy.batch.tutorials.advanced.scanning;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import org.jeasy.batch.core.listener.RecordWriterListener;
import org.jeasy.batch.core.record.Batch;
import org.jeasy.batch.core.record.Record;
import org.jeasy.batch.core.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScannedRecordListener implements RecordWriterListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(ScannedRecordListener.class);

	private FileWriter fileWriter;
	private Path skippedRecords;

	public ScannedRecordListener(Path path) throws IOException {
		fileWriter = new FileWriter(path.toFile());
		skippedRecords = path;
	}

	@Override
	public void beforeRecordWriting(Batch batch) {

	}

	@Override
	public void afterRecordWriting(Batch batch) {

	}

	@Override
	public void onRecordWritingException(Batch batch, Throwable throwable) {
		Record record = null;
		try {
			record = batch.iterator().next();
			if (record.getHeader().isScanned()) {
				fileWriter.write(record.getPayload().toString());
				fileWriter.flush();
				fileWriter.write(Utils.LINE_SEPARATOR);
			}
		} catch (IOException e) {
			String errorMessage = String.format("Unable to write skipped record %s to %s",
					record, skippedRecords.toAbsolutePath());
			LOGGER.error(errorMessage, e);
		}
	}
}
