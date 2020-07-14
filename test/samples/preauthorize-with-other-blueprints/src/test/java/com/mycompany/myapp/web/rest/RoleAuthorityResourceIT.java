package com.mycompany.myapp.web.rest;

import com.mycompany.myapp.PreauthorizeApp;
import com.mycompany.myapp.domain.Role;
import com.mycompany.myapp.domain.RoleAuthority;
import com.mycompany.myapp.domain.RoleAuthorityId;
import com.mycompany.myapp.repository.RoleAuthorityRepository;
import com.mycompany.myapp.repository.RoleRepository;
import com.mycompany.myapp.security.AuthoritiesConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.mycompany.myapp.security.AuthoritiesConstants.ROLE_AUTHORITY_READ;
import static com.mycompany.myapp.security.AuthoritiesConstants.ROLE_AUTHORITY_UPDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the {@link RoleAuthorityResource} REST controller.
 */
@SpringBootTest(classes = PreauthorizeApp.class)

@AutoConfigureMockMvc
public class RoleAuthorityResourceIT {

    public static final String DEFAULT_AUTHORITY = "AAAAAAAAAA";
    public static final String UPDATED_AUTHORITY = "BBBBBBBBBB";
    public static final String ROLE_NAME = "ROLE_NAME";

    @Autowired
    private RoleAuthorityRepository roleAuthorityRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restRoleAuthorityMockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private RoleAuthority roleAuthority;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static RoleAuthority createEntity(EntityManager em) {
        RoleAuthority roleAuthority = new RoleAuthority()
            .authority(DEFAULT_AUTHORITY);
        // Add required entity
        Role newRole = RoleResourceIT.createEntity(em);
        Role role = TestUtil.findAll(em, Role.class).stream()
            .filter(x -> x.getName().equals(newRole.getName()))
            .findAny().orElse(null);
        if (role == null) {
            role = newRole;
            em.persist(role);
            em.flush();
        }
        roleAuthority.setRole(role);
        roleAuthority.setId(new RoleAuthorityId(DEFAULT_AUTHORITY, role.getName()));
        return roleAuthority;
    }
    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static RoleAuthority createUpdatedEntity(EntityManager em) {
        RoleAuthority roleAuthority = new RoleAuthority()
            .authority(UPDATED_AUTHORITY);
        // Add required entity
        Role newRole = RoleResourceIT.createUpdatedEntity(em);
        Role role = TestUtil.findAll(em, Role.class).stream()
            .filter(x -> x.getName().equals(newRole.getName()))
            .findAny().orElse(null);
        if (role == null) {
            role = newRole;
            em.persist(role);
            em.flush();
        }
        roleAuthority.setRole(role);
        roleAuthority.setId(new RoleAuthorityId(UPDATED_AUTHORITY, role.getName()));
        return roleAuthority;
    }

    @BeforeEach
    public void initTest() {
        roleAuthority = createEntity(em);
    }

    public void createRoleAuthority(String roleName, String authority) throws Exception {
        Role role = new Role();
        role.setName(roleName);
        roleRepository.saveAndFlush(role);
        RoleAuthority roleAuth = new RoleAuthority();
        roleAuth.setAuthority(authority);
        roleAuth.setRole(role);
        RoleAuthorityId roleAuthId = new RoleAuthorityId(authority, roleName);
        roleAuth.setId(roleAuthId);
        roleAuthorityRepository.saveAndFlush(roleAuth);
    }

    /**
     * verify that it returns only the authorities that the current user has the right to see
     */
    @Test
    @Transactional
    @WithMockUser(authorities = {ROLE_AUTHORITY_READ, DEFAULT_AUTHORITY})
    public void getAllRoleAuthorities() throws Exception {
        // Initialize the database
        this.createRoleAuthority(AuthoritiesConstants.ADMIN, DEFAULT_AUTHORITY);
        this.createRoleAuthority(AuthoritiesConstants.ADMIN, UPDATED_AUTHORITY);

        // Get all the roleAuthorityList
        ResultActions resultActions = restRoleAuthorityMockMvc.perform(get("/api/role-authorities/{roleName}", AuthoritiesConstants.ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));

        resultActions
            .andExpect(jsonPath("$.[*]").value(hasItem(DEFAULT_AUTHORITY)));
        resultActions
            .andExpect(jsonPath("$.[*]").value(hasSize(1)));
    }

    @Test
    @Transactional
    @WithMockUser(authorities = {ROLE_AUTHORITY_READ, ROLE_AUTHORITY_UPDATE})
    public void getAuthoritiesDependencies() throws Exception {
        // copied from above (java limitation)
        String[] userAuthorities = new String[]{ROLE_AUTHORITY_READ, ROLE_AUTHORITY_UPDATE};
        ResultActions resultActions = restRoleAuthorityMockMvc.perform(get("/api/role-authorities/authorities-dependencies"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.role-authority+update").value(hasItem(ROLE_AUTHORITY_READ)));
        Map<String, List<String>> result = objectMapper.readValue(resultActions.andReturn().getResponse().getContentAsString(), new TypeReference<Map<String, List<String>>>(){});
        assertThat(result.keySet()).containsExactlyInAnyOrder(userAuthorities);
        assertThat(result.values()).allMatch(l -> Arrays.asList(userAuthorities).containsAll(l));
    }

    @Test
    @Transactional
    @WithMockUser(authorities = {ROLE_AUTHORITY_UPDATE, ROLE_AUTHORITY_READ})
    public void updateAdminAuthoritiesShouldFail() throws Exception {
        List<String> authorities = Arrays.asList(ROLE_AUTHORITY_READ);
        restRoleAuthorityMockMvc.perform(put("/api/role-authorities/{roleName}", AuthoritiesConstants.ADMIN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(TestUtil.convertObjectToJsonBytes(authorities)))
            .andExpect(status().is(403));
    }

    @Test
    @Transactional
    @WithMockUser(authorities = {ROLE_AUTHORITY_UPDATE})
    public void updateAuthoritiesShouldFailWhenModifiedRoleHasMoreAuthoritiesThanCurrentUser() throws Exception {

        this.createRoleAuthority(ROLE_NAME, ROLE_AUTHORITY_READ);
        this.createRoleAuthority(ROLE_NAME, ROLE_AUTHORITY_UPDATE);

        List<String> authorities = Arrays.asList(ROLE_AUTHORITY_READ);
        restRoleAuthorityMockMvc.perform(put("/api/role-authorities/{roleName}", ROLE_NAME)
            .contentType(MediaType.APPLICATION_JSON)
            .content(TestUtil.convertObjectToJsonBytes(authorities)))
            .andExpect(status().is(403));
    }

    @Test
    @Transactional
    @WithMockUser(authorities = {ROLE_AUTHORITY_UPDATE})
    public void updateAuthoritiesShouldFailIfCurrentUserTriesToGrantAuthorityHeDoesntHave() throws Exception {
        List<String> authorities = Arrays.asList(ROLE_AUTHORITY_UPDATE, ROLE_AUTHORITY_READ);
        restRoleAuthorityMockMvc.perform(put("/api/role-authorities/{roleName}", ROLE_NAME)
            .contentType(MediaType.APPLICATION_JSON)
            .content(TestUtil.convertObjectToJsonBytes(authorities)))
            .andExpect(status().is(403));
    }
}
