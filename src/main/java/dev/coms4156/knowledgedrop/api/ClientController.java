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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Client registration endpoints. */
@RestController
@RequestMapping("/knowledgeDrop")
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
  @PostMapping("/registerClient")
  public ResponseEntity<RegisterClientResponse> registerClient(
      @Valid @RequestBody RegisterClientRequest request) {
    RegisterClientResponse body = clientService.register(request.clientName());
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  /** Deletes the calling client and all of its data. TODO(clientDrop). */
  @DeleteMapping("/clientDrop")
  public MessageResponse clientDrop(
      @RequestAttribute(AuthInterceptor.CLIENT_ATTRIBUTE) ClientRecord client,
      @RequestParam("clientId") UUID clientId) {
    return clientService.drop(client, clientId);
  }
}
