package com.example.customers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import lombok.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

@EnableCircuitBreaker
@SpringBootApplication
public class CustomersApplication {

	public static void main(String[] args) {
		SpringApplication.run(CustomersApplication.class, args);
	}

	private final AtomicInteger counter = new AtomicInteger();
	private final String names[] = "Manil,Yanis,Reda,Walid,Saeid".split(",");

	private final Flux<Customer> customerFlux = Flux.fromStream(
					Stream.generate(new Supplier<Customer>() {
						@Override
						public Customer get() {
							var id = counter.incrementAndGet();
							return new Customer(id, names[id % names.length]);
						}
					})
			)
			.delayElements(Duration.ofSeconds(2));

	@Bean
	Flux<Customer> customers() {
		return this.customerFlux.publish().autoConnect();
	}

	@Configuration
	@RequiredArgsConstructor
	class WebSocketConfiguration {
		private final ObjectMapper objectMapper;

		@SneakyThrows
		private String from(Customer customer) {
			return this.objectMapper.writeValueAsString(customer);
		}

		@Bean
		WebSocketHandler webSocketHandler(Flux<Customer> customerFlux) {
			return new WebSocketHandler() {
				@Override
				public Mono<Void> handle(WebSocketSession session) {
					Flux<WebSocketMessage> webSocketMessageFlux = customerFlux
							.map(customer -> from(customer))
							.map(session::textMessage);
					return session.send(webSocketMessageFlux);
				}
			};
		}
	}

	@HystrixCommand(fallbackMethod = "fallback")
	@Bean
	SimpleUrlHandlerMapping simpleUrlHandlerMapping(WebSocketHandler customersWebSocketHandler) {
		//throw exception pour tester le hystrix
		return new SimpleUrlHandlerMapping(Map.of("/ws/customers", customersWebSocketHandler), 10);
	}

	public void fallback(Throwable hystrixCommand) {
		System.out.println("Tester le Hystrix");
		return;
	}

	@RestController
	@RequiredArgsConstructor
	class CustomerRestController{

		private final Flux<Customer> customerFlux;
		@GetMapping(
				produces= MediaType.TEXT_EVENT_STREAM_VALUE,
				value = "/customers"
		)
		Flux<Customer> get(){
			return this.customerFlux;
		}
	}


	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public class Customer {
		Integer id;
		String name;
	}
}

