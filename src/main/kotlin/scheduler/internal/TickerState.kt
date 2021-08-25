@file:UseSerializers(ForIdentifier::class, ForBlockPos::class, ForUuid::class, ForNbtCompound::class)

package scheduler.internal

import drawer.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.ListSerializer
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.block.Material
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.registry.Registry
import net.minecraft.world.PersistentState
import net.minecraft.world.World
import scheduler.Scheduleable
import scheduler.Scheduler
import java.util.*
import kotlin.Comparator

@Serializable
internal data class Schedule(
    val context: ScheduleContext = ScheduleContext(),
    val repetition: Repetition = Repetition.Once(),
    // If if not null, a packet will be sent to client and the callback will be executed there.
    // Otherwise, It will just execute on the server.
    val clientRequestingSchedule: UUID? = null,
    val cancellationUUID: UUID = UUID.randomUUID()
) {
    @Transient
    lateinit var scheduleable: Scheduleable
}

@Serializable
internal sealed class Repetition {
    abstract var nextTickTime: Long

    @Serializable
    data class RepeatAmount(override var nextTickTime: Long = 0, val repeatInterval: Int = 1, var amountLeft: Int = 1) :
        Repetition()

    @Serializable
    data class RepeatUntil(override var nextTickTime: Long = 0, val repeatInterval: Int = 1, val stopTime: Long = 0) :
        Repetition()

    @Serializable
    data class Once(override var nextTickTime: Long = 0) : Repetition()
}

@Serializable
internal data class ScheduleContext(
    val blockPos: BlockPos = BlockPos.ORIGIN, val scheduleId: Int = 0,
    val blockId: Identifier = Identifier("minecraft:air"),
    val additionalData: NbtCompound = NbtCompound()
)


internal const val SchedulerId = "working_scheduler"

internal fun getScheduleableFromRegistry(scheduleableBlockId: Identifier): Scheduleable? {
    val block = Registry.BLOCK[scheduleableBlockId]
    if (block == Registry.BLOCK.defaultId) {
        logWarning("Block with id '$scheduleableBlockId' no longer exists.")
        return null
    }
    if (block !is Scheduleable) {
        logWarning("Block $block (id = $scheduleableBlockId) no longer implements Scheduleable.")
        return null
    }
    return block
}

internal class TickerState : PersistentState() {


    private val tickers =
        PriorityQueue<Schedule>(Comparator { a, b -> (a.repetition.nextTickTime - b.repetition.nextTickTime).toInt() })

    override fun writeNbt(nbtCompound: NbtCompound): NbtCompound = nbtCompound
        .also{ ListSerializer(Schedule.serializer()).put(tickers.toList(), it) }

    fun add(ticker: Schedule) {
        tickers.add(ticker)
    }

    val closestToEnd: Schedule get() = tickers.peek()
    val hasAnyTickers: Boolean get() = tickers.isNotEmpty()
    fun removeClosestToEnd(): Schedule? = tickers.poll()
    fun cancel(cancellationUUID: UUID): Boolean = tickers.removeIf { it.cancellationUUID == cancellationUUID }


}

