package com.pixeldrift.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AssetWorkQueueTest {
    @Test
    public void successRemainsAvailableUntilAResumedScreenConsumesIt() {
        long token = AssetWorkQueue.beginOperation(AssetWorkQueue.OperationType.IMPORT);
        assertTrue(AssetWorkQueue.snapshot().isRunning());

        AssetWorkQueue.completeSuccess(token);
        AssetWorkQueue.Snapshot result = AssetWorkQueue.consumeResult();

        assertTrue(result.hasResult());
        assertTrue(result.isSuccess());
        assertEquals(AssetWorkQueue.OperationType.IMPORT, result.getType());
        assertFalse(AssetWorkQueue.snapshot().hasResult());
    }

    @Test
    public void failureDetailSurvivesRotationStateHandoff() {
        long token = AssetWorkQueue.beginOperation(AssetWorkQueue.OperationType.DEMO);
        AssetWorkQueue.completeFailure(token, "disk full");

        AssetWorkQueue.Snapshot result = AssetWorkQueue.consumeResult();

        assertFalse(result.isSuccess());
        assertEquals("disk full", result.getFailureDetail());
    }

    @Test
    public void supersededWorkerCannotCompleteTheNewOperation() {
        long oldToken = AssetWorkQueue.beginOperation(AssetWorkQueue.OperationType.IMPORT);
        long currentToken = AssetWorkQueue.beginOperation(AssetWorkQueue.OperationType.DEMO);

        AssetWorkQueue.completeSuccess(oldToken);
        assertTrue(AssetWorkQueue.snapshot().isRunning());
        assertEquals(AssetWorkQueue.OperationType.DEMO, AssetWorkQueue.snapshot().getType());

        AssetWorkQueue.completeSuccess(currentToken);
        assertTrue(AssetWorkQueue.consumeResult().isSuccess());
    }
}
