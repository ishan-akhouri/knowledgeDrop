package dev.coms4156.knowledgedrop.api;

import dev.coms4156.knowledgedrop.api.dto.MessageResponse;
import dev.coms4156.knowledgedrop.api.dto.RegisterClientRequest;
import dev.coms4156.knowledgedrop.api.dto.RegisterClientResponse;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.security.AuthInterceptor;
import dev.coms4156.knowledgedrop.service.ClientService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Client registration and deletion endpoints. */
@RestController
@RequestMapping("/knowledgeDrop/client")
public class ClientController {

  private final ClientService clientService;

  /**
   * Creates the controller.
   *
   * @param clientService client registration logic
   */
  public ClientController(ClientService clientService) {
    this.clientService = clientService;
  }

  /** Registers a client and returns its ID and token (no token required). Built. */
  @PostMapping("/register")
  public ResponseEntity<RegisterClientResponse> registerClient(
      @Valid @RequestBody RegisterClientRequest request) {
    RegisterClientResponse body = clientService.register(request.name());
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  /** Soft-deletes the calling client and all owned documents. */
  @DeleteMapping("/{clientId}")
  public MessageResponse deleteClient(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @PathVariable("clientId") UUID clientId) {
    return clientService.delete(client, clientId);
  }
}
