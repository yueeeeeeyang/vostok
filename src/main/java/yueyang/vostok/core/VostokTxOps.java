package yueyang.vostok.core;

import yueyang.vostok.config.VKTxIsolation;
import yueyang.vostok.config.VKTxPropagation;
import yueyang.vostok.tx.VKTransactionManager;
import yueyang.vostok.util.VKAssert;

import java.util.function.Supplier;

/**
 * 事务相关操作。
 */
final class VostokTxOps {
    private VostokTxOps() {
    }

    static void tx(Runnable action) {
        tx(action, VKTxPropagation.REQUIRED, VKTxIsolation.DEFAULT, false);
    }

    static void tx(Runnable action, VKTxPropagation propagation, VKTxIsolation isolation) {
        tx(action, propagation, isolation, false);
    }

    static void tx(Runnable action, VKTxPropagation propagation, VKTxIsolation isolation, boolean readOnly) {
        VostokInternal.ensureInit();
        VKAssert.notNull(action, "Transaction action is null");
        boolean started = false;
        try {
            beginTx(propagation, isolation, readOnly);
            started = true;
            action.run();
            VKTransactionManager.commit();
        } catch (RuntimeException e) {
            if (started) {
                VKTransactionManager.rollbackCurrent();
            }
            throw e;
        }
    }

    static <T> T tx(Supplier<T> supplier) {
        return tx(supplier, VKTxPropagation.REQUIRED, VKTxIsolation.DEFAULT, false);
    }

    static <T> T tx(Supplier<T> supplier, VKTxPropagation propagation, VKTxIsolation isolation) {
        return tx(supplier, propagation, isolation, false);
    }

    static <T> T tx(Supplier<T> supplier, VKTxPropagation propagation, VKTxIsolation isolation, boolean readOnly) {
        VostokInternal.ensureInit();
        VKAssert.notNull(supplier, "Transaction supplier is null");
        boolean started = false;
        try {
            beginTx(propagation, isolation, readOnly);
            started = true;
            T result = supplier.get();
            VKTransactionManager.commit();
            return result;
        } catch (RuntimeException e) {
            if (started) {
                VKTransactionManager.rollbackCurrent();
            }
            throw e;
        }
    }

    static void beginTx() {
        beginTx(VKTxPropagation.REQUIRED, VKTxIsolation.DEFAULT, false);
    }

    static void beginTx(VKTxPropagation propagation, VKTxIsolation isolation) {
        beginTx(propagation, isolation, false);
    }

    static void beginTx(VKTxPropagation propagation, VKTxIsolation isolation, boolean readOnly) {
        VostokInternal.ensureInit();
        VKAssert.notNull(propagation, "TxPropagation is null");
        VKAssert.notNull(isolation, "TxIsolation is null");
        if (propagation == VKTxPropagation.REQUIRES_NEW) {
            VKTransactionManager.beginRequiresNew(VostokInternal.currentHolder().getDataSource(), isolation, readOnly,
                    VostokInternal.currentConfig().isSavepointEnabled());
        } else {
            VKTransactionManager.beginRequired(VostokInternal.currentHolder().getDataSource(), isolation, readOnly, propagation,
                    VostokInternal.currentConfig().isSavepointEnabled());
        }
    }

    static void commitTx() {
        VostokInternal.ensureInit();
        VKTransactionManager.commit();
    }

    static void rollbackTx() {
        VostokInternal.ensureInit();
        VKTransactionManager.rollback();
    }
}
