/**
 * Root package of the {@code process-engine-demo} module -- home to {@link com.example.processengine.ProcessEngineApplication}
 * and the {@code demo}/{@code demo.handlers} sample application, and the only module with a
 * runnable Spring Boot context (hence also the whole engine's test suite).
 *
 * <p>The project is split into three Maven modules, one dependency direction only
 * ({@code process-engine-demo -> process-engine-admin -> process-engine-core}, and
 * {@code process-engine-demo -> process-engine-core} directly -- never the reverse):
 * <ul>
 *   <li>{@code process-engine-core} -- the process engine library proper: {@code definition}
 *       (deploy-time graph), {@code runtime} (instance state + event log), {@code engine}
 *       (execution), {@code dto} (the deploy/REST contract, plus factories for building it in
 *       Java), {@code api} (the process-definitions/process-instances REST controllers), and
 *       {@code config}.</li>
 *   <li>{@code process-engine-admin} -- the generic, process-agnostic admin/ops console: the
 *       {@code admin} package (clear-all-data endpoint) and the {@code admin.html} web UI
 *       (deploy, inspect, pause/resume/kill/move-to-step, resolve-ambiguous).</li>
 *   <li>{@code process-engine-demo} (this module) -- the order-fulfillment sample: a concrete
 *       process spec, its {@link com.example.processengine.engine.StepHandler} implementations,
 *       a small business-user-flavored demo page, and the runnable app itself.</li>
 * </ul>
 */
package com.example.processengine;
