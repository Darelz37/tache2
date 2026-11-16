package cm.proclama.web;

import java.util.UUID;

public record AuthUser(UUID id, String email, String name, String role) {
  public boolean isAdmin() {
    return "admin".equals(role);
  }
}
