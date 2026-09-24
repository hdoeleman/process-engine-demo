/**
 * Sample application: a concrete order-fulfillment {@link com.example.processengine.dto.ProcessDefinitionSpec}
 * (built with the engine's own {@link com.example.processengine.dto.StepSpecs}/
 * {@link com.example.processengine.dto.TransitionSpecs} factories, not bespoke code), its
 * {@link com.example.processengine.engine.StepHandler} implementations in {@code demo.handlers},
 * and the startup runner that deploys it. Depends on the engine, never the other way around.
 */
package com.example.processengine.demo;
