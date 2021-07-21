package com.spoon.backgroundfileupload;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;

public final class PendingAcknowledgeTask extends Worker {

    private static final String TAG = "PendingAcknowledgeTask";

    // ACK database
    // <editor-fold>

    /**
     * In-memory thread safe database that acts as a buffer between the app and the tasks.
     * This is not using any sort of 'real' database and nothing will be written to the disk.
     */
    public static class ConcurrentAcknowledgeDatabase {
        private final List<String> pendingAcknowledgements = Collections.synchronizedList(new ArrayList<>());
        private final PublishSubject<String> newestAckSubject = PublishSubject.create();

        public void queueAcknowledgement(final String id) {
            pendingAcknowledgements.add(id);
            // Notify people
            newestAckSubject.onNext(id);
        }

        private boolean checkDeleteAcknowledge(final String id) {
            return pendingAcknowledgements.remove(id);
        }
    }

    private static ConcurrentAcknowledgeDatabase ackDatabaseSingleton = null;

    public static ConcurrentAcknowledgeDatabase getAckDatabase() {
        if (ackDatabaseSingleton == null) {
            ackDatabaseSingleton = new ConcurrentAcknowledgeDatabase();
        }
        return ackDatabaseSingleton;
    }
    // </editor-fold>

    private String idToCheck;
    private final CyclicBarrier waitBarrier = new CyclicBarrier(2);

    public PendingAcknowledgeTask(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Just retransmit the data from the upload task
        setProgressAsync(getInputData());

        idToCheck = getInputData().getString(UploadTask.KEY_OUTPUT_ID);
        assert idToCheck != null;

        Disposable subscriberHandle = getAckDatabase().newestAckSubject.subscribe(this::onNewAckAdded);

        // Wait for the listener to find the corresponding ACK
        try {
            waitBarrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            // If we were interrupted we can assume that this is because the app was killed or similar.
            // If the barrier was broken its similar to an interruption so treat it similarly.

            Log.i(TAG, "doWork: Queued for retry");

            // So we need to retry later
            return Result.retry();
        } finally {
            subscriberHandle.dispose();
        }

        Log.i(TAG, "doWork: " + getId() + " Finishing ack task");
        return Result.success();
    }

    @Override
    public void onStopped() {
        waitBarrier.reset();
    }

    /**
     * This is triggered each time an ACK is added to the global database.
     */
    private void onNewAckAdded(final String newId) {
        if (idToCheck.equals(newId)) {
            Log.i(TAG, "onNewAckAdded: " + getId() + " Received ACK");

            // Delete ack from database
            if (!getAckDatabase().checkDeleteAcknowledge(newId)) {
                // What the fuck
                throw new IllegalStateException("What the fuck, another task must have claimed our ACK");
            }

            // Delete response file if any
            if (getInputData().getString(UploadTask.KEY_OUTPUT_RESPONSE_FILE) != null) {
                getApplicationContext().deleteFile(getInputData().getString(UploadTask.KEY_OUTPUT_RESPONSE_FILE));
            }

            try {
                waitBarrier.await();
            } catch (BrokenBarrierException | InterruptedException ignored) {
                // Doesn't really matter at this point since its just to finish the task
            }
        }
    }
}
