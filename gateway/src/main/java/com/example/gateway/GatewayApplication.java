package com.example.gateway;

import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.SetPathGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

	@Bean
	RouteLocator gateway(RouteLocatorBuilder routeLocatorBuilder){
		return routeLocatorBuilder
				.routes()
				.route("customers", routeSpec -> routeSpec
						.path("/customers")
						.uri("http://localhost:9200/customers") // ou simplement mettre "lb//customers/" <-> ça permet
						//de faire appel au spring load balancer et de resolver le DNS par lui même.
						//flemme de faire ça car mon hostname est different du localhost (ElMachina)
				)
				.route("Google",routeSpec -> routeSpec
						.path("/hello")
						.filters(gatewayFilterSpec -> gatewayFilterSpec
								.setPath("/"))
						.uri("https://google.com")
				)
				.route("Twitter",routeSpec -> routeSpec
						.path("/twitter/**")
						.filters(gatewayFilterSpec -> gatewayFilterSpec
								.rewritePath(
										"/twitter/(?<handle>.*)",
										"/${handle}"
								))
						.uri("http://twitter.com/@"))
				.route("HostTest",routeSpec -> routeSpec
						.path("/test")
						.and()
						.host("*.spring.io")
						.filters(gatewayFilterSpec -> gatewayFilterSpec
								.setPath("/guides"))
						.uri("https://spring.io/"))
				.build();
	}

	@Bean
	RouteLocator gateway2(SetPathGatewayFilterFactory setPathGatewayFilterFactory){
		var singleRoute = Route.async()
				.id("test-route")
				.uri("http://localhost:9200/customers")
				.filters(new OrderedGatewayFilter( setPathGatewayFilterFactory.apply(config -> config.setTemplate("/customers")),1))
				.asyncPredicate(serverWebExchange -> {
					var uri = serverWebExchange.getRequest().getURI();
					var path = uri.getPath();
					var match = path.contains("/customers2");
					return Mono.just(match);
				})
				.build();
		return () -> Flux.just(singleRoute);
	}
}
