package net.cordacon.example;

import net.corda.simulator.HoldingIdentity;
import net.corda.simulator.RequestData;
import net.corda.simulator.SimulatedVirtualNode;
import net.corda.simulator.Simulator;
import net.corda.simulator.crypto.HsmCategory;
import net.corda.simulator.factories.SimulatorConfigurationBuilder;
import net.corda.v5.application.persistence.PagedQuery;
import net.corda.v5.application.persistence.PersistenceService;
import net.corda.v5.base.types.MemberX500Name;
import net.cordacon.example.rollcall.AbsenceCallResponderFlow;
import net.cordacon.example.rollcall.RollCallFlow;
import net.cordacon.example.rollcall.RollCallInitiationRequest;
import net.cordacon.example.rollcall.RollCallResponderFlow;
import net.cordacon.example.rollcall.TruancyEntity;
import net.cordacon.example.rollcall.TruancyResponderFlow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Timeout(value=5, unit= TimeUnit.MINUTES)
public class RollCallJavaTest {

    @Test
    public void testRollCall(){
        // Given a RollCallFlow that's been uploaded to Corda for a teacher
        Simulator simulator = new Simulator(SimulatorConfigurationBuilder.create()
                .withTimeout(Duration.ofMinutes(2))
                .withPollInterval(Duration.ofMillis(50))
                .build()
        );

        MemberX500Name teacher = MemberX500Name
                .parse("CN=Ben Stein, OU=Economics, O=Glenbrook North High School, L=Chicago, C=US");
        HoldingIdentity teacherId = HoldingIdentity.create(teacher);
        SimulatedVirtualNode teacherVNode = simulator.createVirtualNode(teacherId, RollCallFlow.class);

        // And a key to sign the absence record with
        teacherVNode.generateKey("teacher-key", HsmCategory.LEDGER, "Any Scheme");

        // and recipients with the responder flow and a flow  to respond to absence sub-flow when someone is absent
        // (they return an empty string)
        List<String> students = Stream.of("Albers", "Anderson", "Anheiser", "Busch", "Bueller")
                .map(it-> "CN="+it +", OU=Economics, O=Glenbrook North High School, L=Chicago, C=US")
                .collect(Collectors.toList());
        students.forEach(it ->
                simulator.createVirtualNode(
                        HoldingIdentity.create(MemberX500Name.parse(it)),
                        RollCallResponderFlow.class,
                        AbsenceCallResponderFlow.class
                )
        );

        // And a truanting authority who will be sent the signed absence record
        MemberX500Name truantingAuth = MemberX500Name.parse(
                "O=TruantAuth, L=Chicago, C=US"
        );
        SimulatedVirtualNode truantAuthVNode = simulator.createVirtualNode(
                HoldingIdentity.create(truantingAuth),
                TruancyResponderFlow.class
        );

        // When we invoke the roll call in Corda
        String response = teacherVNode.callFlow(
                RequestData.create(
                        "r1",
                        RollCallFlow.class,
                new RollCallInitiationRequest(truantingAuth)
        ));

        // Then we should get the response back
        String nl = System.lineSeparator();
        Assertions.assertEquals(response, "BEN STEIN: Albers?" + nl +
                "ALBERS: Here!"+ nl +
                "BEN STEIN: Anderson?" + nl +
                "ANDERSON: Here!" + nl +
                "BEN STEIN: Anheiser?" + nl +
                "ANHEISER: Here!" + nl +
                "BEN STEIN: Busch?" + nl +
                "BUSCH: Here!" + nl +
                "BEN STEIN: Bueller?" + nl +
                "BEN STEIN: Bueller?" + nl +
                "BEN STEIN: Bueller?" + nl);

        // And Ferris Bueller's absence should have been signed and sent to the truanting authority
        // Then persisted
        PersistenceService persistenceService = truantAuthVNode.getPersistenceService();
        PagedQuery<TruancyEntity> absenceResponses = persistenceService.findAll(TruancyEntity.class);
        List<TruancyEntity> result = absenceResponses.execute();
        Assertions.assertNotNull(result);
        Assertions.assertEquals(result.size(), 1);
        Assertions.assertEquals("CN=Bueller, OU=Economics, O=Glenbrook North High School, L=Chicago, C=US",
                result.get(0).getName());

        simulator.close();

    }
}
