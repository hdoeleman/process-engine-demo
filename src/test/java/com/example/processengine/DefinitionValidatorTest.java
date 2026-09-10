package com.example.processengine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.dto.StepSpecs;
import com.example.processengine.dto.TransitionSpecs;
import com.example.processengine.engine.ProcessEngine;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Covers the deploy-time structural checks {@link com.example.processengine.definition.DefinitionValidator}
 * added on top of the pre-existing dangling-transition/handler checks: cycle detection,
 * reachable-from-start, can-reach-end, and gateway condition parseability. Each of these catches a
 * graph misconfiguration at deploy time instead of it surfacing as a runtime hang or crash deep
 * inside some instance.
 */
@SpringBootTest
@ActiveProfiles("test")
class DefinitionValidatorTest {

    @Autowired
    private ProcessEngine engine;

    @Test
    void cyclicGraphIsRejectedAtDeploy() {
        // a -> b -> a: a two-node USER_TASK cycle with no END reachable at all
        var cyclic = new ProcessDefinitionSpec(
                "Cyclic",
                "a",
                List.of(StepSpecs.userTask("a", "A"), StepSpecs.userTask("b", "B")),
                List.of(TransitionSpecs.to("a", "b"), TransitionSpecs.to("b", "a")));

        assertThatThrownBy(() -> engine.deploy("cyclic-def", cyclic))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void stepUnreachableFromStartIsRejected() {
        // orphan: declared but no transition ever leads to it from startStepKey "a"
        var orphan = new ProcessDefinitionSpec(
                "Orphan",
                "a",
                List.of(
                        StepSpecs.userTask("a", "A"),
                        StepSpecs.end("end", "End"),
                        StepSpecs.end("orphanEnd", "Orphan End")),
                List.of(TransitionSpecs.to("a", "end")));

        assertThatThrownBy(() -> engine.deploy("orphan-def", orphan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    void stepWithNoPathToEndIsRejected() {
        // a single USER_TASK with an outgoing transition to itself would be a cycle, so instead: a
        // dead-end USER_TASK "b" that validateAcyclic/reachable-from-start both pass, but which never
        // reaches any END step because there's no END declared anywhere in the graph.
        var noEnd = new ProcessDefinitionSpec(
                "NoEnd",
                "a",
                List.of(StepSpecs.userTask("a", "A"), StepSpecs.userTask("b", "B")),
                List.of(TransitionSpecs.to("a", "b")));

        assertThatThrownBy(() -> engine.deploy("no-end-def", noEnd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no path to an END step");
    }

    @Test
    void unparseableGatewayConditionIsRejected() {
        var badCondition = new ProcessDefinitionSpec(
                "BadCondition",
                "gateway",
                List.of(
                        StepSpecs.exclusiveGateway("gateway", "Gateway"),
                        StepSpecs.end("endA", "End A"),
                        StepSpecs.end("endB", "End B")),
                List.of(
                        TransitionSpecs.when("gateway", "endA", "))(bad"),
                        TransitionSpecs.defaultBranch("gateway", "endB")));

        assertThatThrownBy(() -> engine.deploy("bad-condition-def", badCondition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid condition expression");
    }
}
