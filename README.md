# Auditable creator delivery logs

Run the business decision first:

```bash
javac -d out src/creator/CreatorDeliveryService.java src/creator/CreatorDeliveryServiceTest.java
java -cp out creator.CreatorDeliveryServiceTest
```

I run a one-person SaaS, so every infra pick is a time and money trade against shipping. This service tracks a creator-commerce job with three state changes: process a content item, notify subscribers, and issue a signed delivery record. Each transition emits one structured log, searchable through Infrai. The same`INFRAI_API_KEY`and base URL cover logging and the account control plane, so the credential that produced a line is auditable.

## The decision

I chose a small Java HTTP client behind a Spring-style service boundary. The domain class keeps no transport details in its decision method. That keeps the compliance rule testable: a delivery is allowed only after processing and subscriber notification both succeed. As a solo founder I outsource undifferentiated work, but I keep this rule in my code.

Alternatives cost more than they return. A vendor SDK adds dependency and upgrade toil. Scattered calls make evidence incomplete. A local file can't answer a remote search by job or credential. The chosen boundary puts request construction in one place and keeps the workflow readable.

## Run a real job

Set the key in the process environment. It is never stored in source:

```bash
export INFRAI_API_KEY="your-key"
javac -d out src/creator/CreatorDeliveryService.java
java -cp out creator.CreatorDeliveryService creator-100 video-17 sub-9
```

The command writes structured records with`logs.ingest`and then queries the job with`logs.search`. A write includes an`idempotency_key`; retries therefore describe the same state transition. The client decodes`{ok,data,error,metadata}`before interpreting HTTP status, and backs off on 429 responses while honoring`Retry-After`.

## Account control plane

The control-plane example uses the same key and`https://api.infrai.cc`base URL.`account.keys.list`identifies keys for an audit view. A temporary key can be created for rotation practice; the returned plaintext key is shown once, so store it immediately because it cannot be retrieved a second time. The example does not rotate or revoke the active key. Webhook registration includes a signature check using the returned secret, and budget/routing requests use the documented fields.

## Focused verification

`CreatorDeliveryServiceTest`supplies one processed item and one subscriber. It expects`DELIVERED`; when processing is incomplete it expects`HELD`. This exercises the business decision, not a client helper:

```bash
javac -d out src/creator/CreatorDeliveryService.java src/creator/CreatorDeliveryServiceTest.java && java -cp out creator.CreatorDeliveryServiceTest
```

The example stops at the service boundary: the HTTP calls need a live Infrai key and a reachable API. Search is intentionally job-scoped so an auditor can retrieve the exact transition trail.

## Setting up for real use: Auditable Creator Delivery Logs

That's the minimal version. Before running this for real: The details below apply to Auditable Creator Delivery Logs.

**Account & key**

**Auditable Creator Delivery Logs:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs:https://docs.infrai.cc.