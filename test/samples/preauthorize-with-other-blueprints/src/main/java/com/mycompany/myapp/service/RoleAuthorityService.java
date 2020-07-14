package com.mycompany.myapp.service;

import com.mycompany.myapp.domain.Role;
import com.mycompany.myapp.domain.RoleAuthority;
import com.mycompany.myapp.domain.RoleAuthorityId;
import com.mycompany.myapp.domain.User;
import com.mycompany.myapp.repository.RoleAuthorityRepository;
import com.mycompany.myapp.security.AuthoritiesConstants;
import com.mycompany.myapp.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service Implementation for managing {@link RoleAuthority}.
 */
@Service
@Transactional
public class RoleAuthorityService {

    private final Logger log = LoggerFactory.getLogger(RoleAuthorityService.class);

    private final RoleAuthorityRepository roleAuthorityRepository;

    private final UserService userService;

    public RoleAuthorityService(RoleAuthorityRepository roleAuthorityRepository, UserService userService) {
        this.roleAuthorityRepository = roleAuthorityRepository;
        this.userService = userService;
    }

    public void updateAuthorities(List<String> authorities, String roleName) {
        Set<String> currentUserAuthorities = SecurityUtils.getCurrentAuthorities();
        Set<String> roleAuthorities;
        roleAuthorities = roleAuthorityRepository.findDistinctByRoleName(roleName);
        if (!roleName.equals(AuthoritiesConstants.ADMIN) && currentUserAuthorities.containsAll(roleAuthorities) && currentUserAuthorities.containsAll(authorities)) {
            roleAuthorityRepository.deleteRoleAuthorityByRoleName(roleName);
            Role role = new Role();
            role.setName(roleName);
            for(String auth: authorities) {
                RoleAuthority roleAuthority = new RoleAuthority();
                RoleAuthorityId roleAuthorityId = new RoleAuthorityId(auth, roleName);
                roleAuthority.setId(roleAuthorityId);
                roleAuthority.setAuthority(auth);
                roleAuthority.setRole(role);
                roleAuthorityRepository.save(roleAuthority);
            }
        } else {
            User user = userService.getUserWithRoles().get();
            log.warn("user: " + user.getLogin() + "tried updating role" + roleName + "with authorities" + authorities + "while himself having role: " + user.getRoles() + "and authorities: " + currentUserAuthorities);
            throw new AccessDeniedException("Forbidden");
        }
    }

    public Map<String, List<String>> getAuthoritiesDependencies() {
        Set<String> currentUserAuthorities = SecurityUtils.getCurrentAuthorities();
        return AuthoritiesConstants.AUTHORITIES_TREE.entrySet().stream().filter(e -> currentUserAuthorities.contains(e.getKey()))
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().stream().filter(a -> currentUserAuthorities.contains(a)).collect(Collectors.toList())));
    }

    public Set<String> findByRoleName(String roleName) {
        Set<String> currentUserAuthorities = SecurityUtils.getCurrentAuthorities();
        Set<String> roleAuthorities = roleAuthorityRepository.findDistinctByRoleName(roleName);
        roleAuthorities.retainAll(currentUserAuthorities);
        return roleAuthorities;
    }
}
