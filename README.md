# process-engine-demo

A sample application for [process-engine](../process-engine): bundled processes with their handlers, a
Human Tasks page and the engine's admin console. It consumes `process-engine-core` and
`process-engine-admin` as ordinary dependencies and is not part of the library's build.

## Run

```bash
(cd ../process-engine && ./mvnw install)   # once, and after any change to the library
./mvnw spring-boot:run                      # http://localhost:8080
```

| URL | Page |
|---|---|
| `/` | Human Tasks: complete the manual steps waiting for a person (processes are started from the admin console) |
| `/admin.html` | Admin console: deploy, inspect and operate instances |
| `/editor.html` | Diagram editor |
| `/swagger-ui.html` | Swagger UI over the engine's and the console's OpenAPI documents |

## Bundled processes

Deployed at startup when `process-engine.demo.load-sample-definition` is true (the default).

| Key | Shows |
|---|---|
| `order-fulfillment` (v1, v2) | Service and user tasks, fork/join, exclusive gateway, retry, saga compensation; v2 adds a `SUB_PROCESS` |
| `customer-notification` | The process called by `order-fulfillment` v2 |
| `activate-insurance`, `activate-insurance-rollback` | Exclusive gateway, sub-process, failure routing to a rollback process |
| `send-pdf`, `send-pdf-rollback` | Timer wait, exclusive gateway, sub-process, failure routing |
| `multi-supplier-quote` | Fan-out sub-process over a list of suppliers |
| `supplier-quote` | Event-based gateway: a timer against a user task |

The `order-fulfillment` handlers honor these start variables, for trying retries and rollback:

- `<stepKey>Fail=true`: the step fails on every attempt, as a call that did not take effect; add
  `<stepKey>FailErrorCode` to make it a refusal (a `BusinessError`), which fails the step at once.
- `<stepKey>FailUnknown=true`: the call ends with an exception that does not say whether it took effect, so the step
  becomes `OUTCOME_UNKNOWN` for an operator to resolve: at once when it is not idempotent, and once its attempts run
  out when it is.
- `<stepKey>Slow=true`: the step pauses 11 s, which trips a step timeout set below that.

```bash
ID=$(curl -s -X POST "localhost:8080/api/process-instances?processKey=order-fulfillment" \
  -H "Content-Type: application/json" -d '{"variables":{"orderId":"ORD-1"}}' | jq -r .id)
STEP=$(curl -s localhost:8080/api/process-instances/$ID | jq -r '.steps[]|select(.stepKey=="reviewOrder")|.id')
curl -s -X POST localhost:8080/api/process-instances/$ID/steps/$STEP/complete \
  -H "Content-Type: application/json" -d '{}'
curl -s localhost:8080/api/process-instances/$ID | jq '.status,.variables'
```

Try `{"orderId":"ORD-1","generateInvoiceFail":true}` to see the completed branches compensated, and
`"simulateDeclined":true` to route to `cancelOrder`.

## Data

A file-backed H2 database in `./data`, kept across restarts. Stop the app and delete `data/` for a clean
start, or use "Clear all data" in the admin console (enabled in this application's `application.yml`),
then "Redeploy bundled processes" on the Human Tasks page.

## Build

`./mvnw verify` runs Checkstyle, Spotless, the tests (including architecture rules) and coverage floors.
`./mvnw -Pmutation verify` adds mutation testing and `./mvnw -Psecurity verify` the dependency scan; see the
library's [README](../process-engine/README.md#build). The engine's own integration tests live in the
library.
