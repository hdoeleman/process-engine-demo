package com.example.processengine.demo.handlers;

import com.example.processengine.engine.CompensatingStepHandler;
import com.example.processengine.engine.StepExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Implements {@link CompensatingStepHandler}: an issued invoice is a real document a downstream
 *  system (billing, the customer's inbox) may already have seen, so a saga rollback or operator
 *  rewind must void it rather than just forgetting the id. */
@Component("generateInvoice")
public class GenerateInvoiceHandler implements CompensatingStepHandler {

    private static final Logger log = LoggerFactory.getLogger(GenerateInvoiceHandler.class);
    private static final int INVOICE_ID_SUFFIX_LENGTH = 8;

    private final SimulatedLatency latency;

    public GenerateInvoiceHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        var invoiceId = "INV-" + ctx.getProcessInstanceId().toString().substring(0, INVOICE_ID_SUFFIX_LENGTH);
        log.info("[{}] generated invoice {}", ctx.getProcessInstanceId(), invoiceId);
        ctx.setVariable("invoiceId", invoiceId);
    }

    @Override
    public void compensate(StepExecutionContext ctx) {
        var invoiceId = ctx.getVariable("invoiceId");
        log.info("[{}] voided invoice {}", ctx.getProcessInstanceId(), invoiceId);
        ctx.setVariable("invoiceVoided", true);
    }
}
