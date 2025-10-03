package fr.univtln.pegliasco.tp;

import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import io.github.cdimascio.dotenv.Dotenv;
import reactor.core.publisher.Mono;

public class MyBot {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        String token = dotenv.get("DISCORD_TOKEN");

        DiscordClient client = DiscordClient.create(token);

        Mono<Void> login = client.withGateway((GatewayDiscordClient gateway) -> {
            // ReadyEvent example
            Mono<Void> printOnLogin = gateway.on(ReadyEvent.class, event ->
                            Mono.fromRunnable(() -> {
                                final User self = event.getSelf();
                                System.out.printf("Logged in as %s#%s%n", self.getUsername(), self.getDiscriminator());
                            }))
                    .then();

            // MessageCreateEvent example
            System.out.println("Bot démarré, en attente de messages...");
            Mono<Void> handlePingCommand = gateway.on(MessageCreateEvent.class, event -> {
                        System.out.println("Message reçu : " + event.getMessage().getContent());

                        Message message = event.getMessage();

                        if (message.getContent().equalsIgnoreCase("!ping")) {
                            return message.getChannel()
                                    .flatMap(channel -> channel.createMessage("pong!"));
                        }

                        return Mono.empty();
                    })
                    .onErrorContinue((throwable, o) -> {
                        System.err.println("Erreur lors du traitement d’un message :");
                        throwable.printStackTrace();
                    })

                    .then();


            // combine them!
            return printOnLogin.and(handlePingCommand);
        });

        login.block();
    }
}
