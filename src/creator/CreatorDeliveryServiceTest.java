package creator;

public final class CreatorDeliveryServiceTest {
    public static void main(String[] args) {
        CreatorDeliveryService service = new CreatorDeliveryService(null);
        CreatorDeliveryService.Job ready = new CreatorDeliveryService.Job("job-1", "asset-1", "sub-1", true, true);
        CreatorDeliveryService.Job pending = new CreatorDeliveryService.Job("job-2", "asset-2", "sub-2", false, true);
        if (!"DELIVERED".equals(service.decide(ready))) throw new AssertionError("ready job must deliver");
        if (!"HELD".equals(service.decide(pending))) throw new AssertionError("unprocessed job must be held");
        for (String envelope : new String[]{"{\"ok\":true}", "{\"ok\" : \n true, \"data\": [null, 1.5, {\"text\":\"escaped \\\"quote\"}]}"}) {
            if (!CreatorDeliveryService.InfraiClient.envelopeOk(envelope)) throw new AssertionError("valid success rejected");
        }
        for (String envelope : new String[]{"{\"ok\":false,\"data\":{\"ok\":true}}", "{\"ok\":\"true\"}", "{\"data\":{\"ok\":true}}"}) {
            if (CreatorDeliveryService.InfraiClient.envelopeOk(envelope)) throw new AssertionError("false success accepted");
        }
        try {
            CreatorDeliveryService.InfraiClient.envelopeOk("{\"ok\":true} trailing");
            throw new AssertionError("invalid JSON accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("business decision verified: DELIVERED and HELD");
    }
}
