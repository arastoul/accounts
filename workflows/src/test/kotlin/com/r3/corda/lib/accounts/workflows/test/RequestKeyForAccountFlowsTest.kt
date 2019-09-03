package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.hamcrest.CoreMatchers.`is`
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class RequestKeyForAccountFlowsTest {

    lateinit var network: MockNetwork
    lateinit var nodeA: StartedMockNode
    lateinit var nodeB: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
                MockNetworkParameters(
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                        cordappsForAllNodes = listOf(
                                TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                                TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows")
                        )
                )
        )
        nodeA = network.createPartyNode()
        nodeB = network.createPartyNode()

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    //checks if the flow returns nodeA public key for the account
    @Test
    fun `should return key when a node requests`() {
        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA")).runAndGet(network)
        // nodeB requesting key for account
        val confidentIdentityA = nodeB.startFlow(RequestKeyForAccount(accountA.state.data)).runAndGet(network)
        val accountServiceA = nodeA.services.cordaService(KeyManagementBackedAccountService::class.java)
        val accountServiceB = nodeB.services.cordaService(KeyManagementBackedAccountService::class.java)
        //verify that nodeA key is returned
        Assert.assertNotNull(confidentIdentityA)
        nodeB.transaction {
            //check if the the key was actually generated by node nodeA
            accountServiceB.services.identityService.requireWellKnownPartyFromAnonymous(confidentIdentityA)
        }
        val keysForAccountA = nodeA.transaction {
            accountServiceA.accountKeys(accountA.state.data.identifier.id)
        }
        Assert.assertEquals(keysForAccountA, listOf(confidentIdentityA.owningKey))

    }

    //check if it is possible to access account using the public key generated
    @Test
    fun `should be possible to get the account by newly created key`(){
        val accountA=nodeA.startFlow(CreateAccount("Test_AccountA")).runAndGet(network)
        //nodeB request public key
        val confidentIdentityA=nodeB.startFlow((RequestKeyForAccount(accountA.state.data))).runAndGet(network)
        val accountService = nodeA.services.cordaService(KeyManagementBackedAccountService::class.java)
        nodeA.transaction{
            //access the account using accountInfo method ,passing the Public key as parameter
            // and check if the account returned is 'accountA'.
            Assert.assertThat(accountService.accountInfo(confidentIdentityA.owningKey), `is`(accountA))
        }


    }
}



