package io.maslick.fluxy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.minidev.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.xml.Jaxb2XmlDecoder;
import org.springframework.http.codec.xml.Jaxb2XmlEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import javax.xml.bind.annotation.XmlRootElement;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

@SpringBootApplication
public class FluxyApp {
	public static void main(String[] args) {
		SpringApplication.run(FluxyApp.class, args);
	}
}

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@XmlRootElement
class Item {
	private String id;
	private String name;
}

@RestController
@RequiredArgsConstructor
class REST {
	final Repo repo;

	@GetMapping(value = "/array", produces = "application/json")
	Flux<Item> jsonArray() {
		return repo.findAll();
	}

	@GetMapping(value = "/stream", produces = "application/stream+json")
	Flux<Item> jsonStream() {
		return repo.findAll();
	}

	@GetMapping(value = "/events", produces = "text/event-stream")
	Flux<ServerSentEvent<Item>> jsonEvents() {
		return repo.findAll()
				.map(item -> ServerSentEvent.<Item>builder()
						.event("data")
						.data(item)
						.build()
				);
	}
}

@Repository
class Repo {
	Flux<Item> findAll() {
		return Flux.interval(Duration.ofSeconds(1))
				.takeWhile(i -> i < 9)
				.map(i -> i.intValue() + 1)
				.map(i -> Item.builder()
						.id(i.toString())
						.name(UUID.randomUUID().toString())
						.build()
				);
	}
}

@Configuration
class MyConf {
	@Value("${web.url}") String url;
	@Value("${web.key}") String key;

	@Bean("url")
	public String getUrl() {
		return url;
	}

	@Bean("key")
	public String getApiKey() {
		return key;
	}
}

@Configuration
class WebConfig implements WebFluxConfigurer {
	@Override
	public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
		configurer.registerDefaults(true);
		configurer.customCodecs().decoder(new Jaxb2XmlDecoder());
		configurer.customCodecs().encoder(new Jaxb2XmlEncoder());

	}
}

@Component
@RequiredArgsConstructor
class HttpBinCaller {
	private final @Qualifier("url") String url;
	private final @Qualifier("key") String key;

	@PostConstruct
	public void init() {
		System.out.println("URL: " + url);
		System.out.println("API key: " + key);

		WebClient client = WebClient.builder()
				.baseUrl("http://httpbin.org")
				.build();

		client.get()
				.uri("/get")
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.bodyToFlux(JSONObject.class)
				.map(json -> Arrays.asList(json.get("origin").toString().split(", ")))
				.flatMap(Flux::fromIterable)
				.subscribe(ip -> System.out.println("origin: " + ip));

	}
}