package chat.simplex.common.views.wallet

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * WorkManager workers for background wallet operations.
 * Runs even when the app is backgrounded / killed.
 *
 * Workers:
 *  1. BalanceSyncWorker  – periodic balance refresh
 *  2. PendingSwapWorker  – poll pending swap confirmations
 *  3. TransferScanWorker – scan for incoming ERC-20 transfers
 */

// ═══════════════════════════════════════════════════════════════════
//  Balance sync
// ═══════════════════════════════════════════════════════════════════

class BalanceSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            WalletCache.clearBalances()
            // Trigger a full balance refresh for all active accounts
            val profiles = WalletCoreService.getWalletProfiles()
            val activeProfile = profiles.find { it.isActive } ?: profiles.firstOrNull()
            if (activeProfile != null) {
                WalletCoreService.refreshAllBalances(activeProfile.id)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Pending swap confirmation
// ═══════════════════════════════════════════════════════════════════

class PendingSwapWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            SwapManager.resumePendingSwaps()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Incoming transfer scan
// ═══════════════════════════════════════════════════════════════════

class TransferScanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private val watchableNetworks = setOf(
        *BlockchainNetwork.EVM_NETWORKS.toTypedArray(),
        BlockchainNetwork.TRON,
        BlockchainNetwork.SOLANA,
        BlockchainNetwork.BITCOIN
    )

    override suspend fun doWork(): Result {
        return try {
            val profiles = WalletCoreService.getWalletProfiles()
            val activeProfile = profiles.find { it.isActive } ?: return Result.success()
            val accounts = WalletCoreService.getAccounts(activeProfile.id)
            accounts.filter { it.network in watchableNetworks }.forEach { account ->
                BlockchainService.watchIncomingTransfers(account.address, account.network, 0)
            }
            WalletCoreService.updatePendingTransactions()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Scheduler
// ═══════════════════════════════════════════════════════════════════

object WalletWorkScheduler {

    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)

        // Periodic balance sync every 15 minutes (minimum)
        val balanceWork = PeriodicWorkRequestBuilder<BalanceSyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(ProductionConfig.WORK_BALANCE_SYNC)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        wm.enqueueUniquePeriodicWork(
            ProductionConfig.WORK_BALANCE_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            balanceWork
        )

        // Periodic pending swap check every 15 minutes
        val swapWork = PeriodicWorkRequestBuilder<PendingSwapWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(ProductionConfig.WORK_PENDING_SWAP_CHECK)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        wm.enqueueUniquePeriodicWork(
            ProductionConfig.WORK_PENDING_SWAP_CHECK,
            ExistingPeriodicWorkPolicy.KEEP,
            swapWork
        )

        // Periodic incoming transfer scan every 15 minutes
        val scanWork = PeriodicWorkRequestBuilder<TransferScanWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(ProductionConfig.WORK_TRANSFER_SCAN)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        wm.enqueueUniquePeriodicWork(
            ProductionConfig.WORK_TRANSFER_SCAN,
            ExistingPeriodicWorkPolicy.KEEP,
            scanWork
        )
    }

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(ProductionConfig.WORK_BALANCE_SYNC)
        wm.cancelUniqueWork(ProductionConfig.WORK_PENDING_SWAP_CHECK)
        wm.cancelUniqueWork(ProductionConfig.WORK_TRANSFER_SCAN)
    }
}
