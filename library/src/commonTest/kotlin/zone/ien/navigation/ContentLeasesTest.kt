package zone.ien.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentLeasesTest {

    @Test
    fun ownerComposesWhenNobodyHoldsTheKey() {
        val leases = ContentLeases()
        leases.acquire("detail", owner = 1)
        assertTrue(leases.canCompose("detail", 1))
        assertFalse(leases.canCompose("detail", 2))
    }

    @Test
    fun screenThatNeverAppearedDoesNotCompose() {
        assertFalse(ContentLeases().canCompose("detail", 1))
    }

    @Test
    fun newOwnerWaitsUntilThePreviousHolderReleases() {
        val leases = ContentLeases()
        leases.acquire("detail", 1)
        leases.hold("detail", 1)

        // The same route is pushed again and its screen appears.
        leases.acquire("detail", 2)
        assertFalse(leases.canCompose("detail", 1), "previous screen must drop its content")
        assertFalse(leases.canCompose("detail", 2), "new screen must wait for the release")

        leases.release("detail", 1)
        assertTrue(leases.canCompose("detail", 2))
    }

    @Test
    fun leaseReturnsWhenTheCoveredScreenAppearsAgain() {
        val leases = ContentLeases()
        leases.acquire("detail", 1)
        leases.acquire("detail", 2)
        leases.hold("detail", 2)

        // The top copy is popped: the covered one appears again and takes the lease, so the top
        // copy (still in the window during the pop) drops its content.
        leases.acquire("detail", 1)
        assertFalse(leases.canCompose("detail", 2))
        leases.release("detail", 2)
        leases.forget("detail", 2)
        assertTrue(leases.canCompose("detail", 1))
    }

    @Test
    fun removedScreenKeepsTheKeyUntilItsContentLeaves() {
        val leases = ContentLeases()
        leases.acquire("detail", 1)
        leases.hold("detail", 1)

        // The screen is popped (removed while its content is still composed, off the window),
        // then the same route is pushed again.
        leases.forget("detail", 1)
        leases.acquire("detail", 2)
        assertFalse(leases.canCompose("detail", 2), "the removed content is still registered")

        leases.release("detail", 1)
        assertTrue(leases.canCompose("detail", 2))
    }

    @Test
    fun releaseByANonHolderIsIgnored() {
        val leases = ContentLeases()
        leases.acquire("detail", 2)
        leases.hold("detail", 2)
        leases.release("detail", 1)
        assertTrue(leases.canCompose("detail", 2))
    }
}
