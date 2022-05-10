//package net.corda.reconciliation.impl
//
//import net.corda.reconciliation.ReconcilerReader
//import net.corda.reconciliation.ReconcilerWriter
//import net.corda.reconciliation.VersionedRecord
//import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
//import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
//
//import net.corda.lifecycle.LifecycleCoordinatorName
//import org.junit.jupiter.api.Assertions
//import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Test
//import java.time.Duration
//
//class MyDBReconcilerReader : ReconcilerReader<String, String>
//{
//    private val list : MutableList<VersionedRecord<String, String>> = mutableListOf()
//
//    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<MyDBReconcilerReader>()
//
//    init
//    {
//        list.add(VersionedRecord<String, String>(1, "one", "abc"))
//        list.add(VersionedRecord<String, String>(1, "two", "def"))
//        list.add(VersionedRecord<String, String>(1, "three", "ghi"))
//    }
//
//    fun add(vr : VersionedRecord<String, String>)
//    {
//        list.add(vr)
//    }
//
//    fun removeAt(index : Int)
//    {
//        list.removeAt(index)
//    }
//
//    override fun getAllVersionedRecords(): Sequence<VersionedRecord<String, String>>
//    {
//        return list.asSequence()
//    }
//}
//
//class MyKafkaReconcilerReader : ReconcilerReader<String, String>
//{
//    private val list : MutableList<VersionedRecord<String, String>> = mutableListOf()
//
//    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<MyKafkaReconcilerReader>()
//
//    init
//    {
//        list.add(VersionedRecord<String, String>(1, "one", "abc"))
//        list.add(VersionedRecord<String, String>(1, "two", "def"))
//        list.add(VersionedRecord<String, String>(1, "three", "ghi"))
//    }
//
//    fun add(vr : VersionedRecord<String, String>)
//    {
//        list.add(vr)
//    }
//
//    fun removeAt(index : Int)
//    {
//        list.removeAt(index)
//    }
//
//    override fun getAllVersionedRecords(): Sequence<VersionedRecord<String, String>>
//    {
//        return list.asSequence()
//    }
//}
//
//class MyReconcilerWriter : ReconcilerWriter<String>
//{
//    // private
//    val list : MutableList<String> = mutableListOf()
//
//    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<MyReconcilerWriter>()
//
//    override fun put(toBePublished: String)
//    {
//        list.add(toBePublished)
//    }
//}
//
//class ReconcilerTest {
//    private lateinit var dbRR : MyDBReconcilerReader
//    private lateinit var kRR : MyKafkaReconcilerReader
//    private lateinit var theRW : MyReconcilerWriter
//
//    @BeforeEach
//    fun beforeEach()
//    {
//        dbRR = MyDBReconcilerReader()
//        kRR = MyKafkaReconcilerReader()
//        theRW = MyReconcilerWriter()
//    }
//
//    @Test
//    fun `check stuff`()
//    {
//        val vr1 = VersionedRecord<String, String>(1, "one", "abc")
//        val vr2 = VersionedRecord<String, String>(1, "two", "def")
//
//        Assertions.assertNotNull(dbRR.getAllVersionedRecords().find { it == vr1 })
//        Assertions.assertNotNull(kRR.getAllVersionedRecords().find { it == vr2 })
//    }
//
//    @Test
//    fun `reconcile nothing`()
//    {
//        val lcfi = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
//        val rfi = ReconcilerFactoryImpl(lcfi)
//        val r = rfi.create(dbRR, kRR, theRW,
//                String::class.java, String::class.java,
//                Duration.ofMillis(1000))
//
//        r.reconcile()
//
//        Assertions.assertEquals(theRW.list.size, 0)
//    }
//
//    @Test
//    fun `reconcile 1 from db`()
//    {
//        val lcfi = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
//        val rfi = ReconcilerFactoryImpl(lcfi)
//        val r = rfi.create(dbRR, kRR, theRW,
//                String::class.java, String::class.java,
//                Duration.ofMillis(1000))
//
//        kRR.removeAt(0)
//
//        r.reconcile()
////        r.start()
////        r.stop()
//
//        Assertions.assertEquals(theRW.list.size, 1)
//    }
//
//    @Test
//    fun `reconcile 2 from db`()
//    {
//        val lcfi = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
//        val rfi = ReconcilerFactoryImpl(lcfi)
//        val r = rfi.create(dbRR, kRR, theRW,
//                String::class.java, String::class.java,
//                Duration.ofMillis(1000))
//
//        kRR.removeAt(0)
//        kRR.removeAt(0)
//
//        r.reconcile()
////        r.start()
////        r.stop()
//
//        Assertions.assertEquals(theRW.list.size, 2)
//    }
//}
