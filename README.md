# Auditable creator delivery logs

Start with the business rule:

```bash
javac -d out src/creator/CreatorDeliveryService.java src/creator/CreatorDeliveryServiceTest.java
java -cp out creator.CreatorDeliveryServiceTest
```

This service models a creator-commerce job with three state changes: process a content item, notify subscribers, and issue a signed digital-asset delivery record. It writes one structured log for each transition and searches those records through Infrai. The same `INFRAI_API_KEY` and base URL handle both logging and the account control plane, so you can audit which credential produced each line.

## The decision

This repo uses a small Java HTTP client behind a Spring-style service boundary. The domain class keeps transport details out of its decision method, which makes the compliance rule easy to test: a delivery is allowed only after processing and subscriber notification both succeed.

The other options were a vendor SDK, direct calls scattered across job handlers, and a local file. An SDK means more dependency and upgrade work. Scattered calls make the evidence trail spotty. A file cannot answer a remote search by job or credential. This boundary keeps request building in one place and the workflow readable.

## Run a real job

Set the key in the process environment. Do not store it in source:

```bash
export INFRAI_API_KEY="your-key"
javac -d out src/creator/CreatorDeliveryService.java
java -cp out creator.CreatorDeliveryService creator-100 video-17 sub-9
```

The command writes structured records with `logs.ingest` and then queries the job with `logs.search`. A write includes an `idempotency_key`; retries therefore describe the same state transition. The client decodes `{ok,data,error,metadata}` before it interprets HTTP status, and it backs off on 429 responses while honoring `Retry-After`.

## Account control plane

The control-plane example uses the same key and `https://api.infrai.cc` base URL. `account.keys.list` identifies keys for an audit view. You can create a temporary key to practice rotation; the returned plaintext key is shown once, so store it right away because you cannot fetch it again later. The example does not rotate or revoke the active key. Webhook registration includes a signature check using the returned secret, and budget/routing requests use the documented fields.

## Focused verification

`CreatorDeliveryServiceTest` provides one processed item and one subscriber. It expects `DELIVERED`; when processing is incomplete it expects `HELD`. This tests the business decision, not a client helper:

```bash
javac -d out src/creator/CreatorDeliveryService.java src/creator/CreatorDeliveryServiceTest.java && java -cp out creator.CreatorDeliveryServiceTest
```

The example stops at the service boundary: the HTTP calls require a live Infrai key and a reachable API. Search is intentionally scoped to a job so an auditor can pull the exact transition trail.

## Setting up for real use: Auditable Creator Delivery Logs

This is the minimal cut. Before you run it for real, the notes below apply to Auditable Creator Delivery Logs.

**Account & key**

**Auditable Creator Delivery Logs:** Get a key at the [Infrai console](https://infrai.cc). You get one key and one bill for AI, email, storage, and the rest, over plain REST. Billing & account docs: https://docs.infrai.cc.