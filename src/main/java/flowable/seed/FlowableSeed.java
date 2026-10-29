package flowable.seed;

import java.util.Arrays;
import java.util.List;

import org.flowable.idm.api.IdmIdentityService;
import org.flowable.idm.api.Group;
import org.flowable.idm.api.Privilege;
import org.flowable.idm.api.User;

public class FlowableSeed {
    private final IdmIdentityService idmIdentityService;

    public FlowableSeed(IdmIdentityService idmIdentityService) {
        this.idmIdentityService = idmIdentityService;
    }

    public void seed() {
        createGroupAdmin();
        asignPrivilegesAdmin();

        seedUser("valentin", "Valentin", "Marcial", "marcialvalentin190@gmail.com", "Valen");
        seedUser("juan","Juan Cruz", "Reynoso", "reynoso@gmail.com",  "Juan");
        seedUser("matiasP","Matias", "Palacios", "matipalacios@gmail.com", "MatiP"); 
        seedUser("matiasB","Matias", "Bricco", "matibrico@gmail.com", "MatiB");
        seedUser("marcelo","Marcelo", "Juarez", "marcelojuarez@gmail.com",  "Marce");

        createUserAdmin("valentin");
        createUserAdmin("juan");
        createUserAdmin("matiasP");
        createUserAdmin("matiasB");
        createUserAdmin("marcelo");

        seedGroup("Administracion", "Administracion de cursos");
        seedGroup("Docentes", "Docentes de los cursos");

        seedUser("jefeAdministrativo","Jefe", "administrativo", "jefeAdministrativo@gmail.com",  "JefeA");
        seedUser("empleadoAdministrativo","Empleado", "administrativo", "empleadoAdministrativo@gmail.com",  "EmpleadoA");

        setMembership("jefeAdministrativo", "Administracion"); // Jefe admnistrativo
        setMembership("empleadoAdministrativo", "Administracion"); // Empleado administrativo

        seedUser("jefeDocente","Jefe", "docente", "jefeDocente@gmail.com",  "JefeD123");
        seedUser("docente1","Docente", "1", "docente1@gmail.com",  "Docente1");
        seedUser("docente2","Docente", "2", "docente2@gmail.com",  "Docente2");

        setMembership("jefeDocente", "Docentes"); // Jefe docente
        setMembership("docente1", "Docentes"); // Docente 1
        setMembership("docente2", "Docentes"); // Docente 2
    }

    public void seedGroup(String groupId, String groupName ) {
        if ( !groupId.equals("Admin") && idmIdentityService.createGroupQuery().groupId(groupId).singleResult() == null) {
            Group group = idmIdentityService.newGroup(groupId);
            group.setName(groupName);
            group.setType("assignment");
            idmIdentityService.saveGroup(group);
        } else {
            System.out.println("No es posible crear el grupo");
        }
    }

    public void seedUser(String userId, String userName, String userLastName, String userEmail, String password) {
        if (idmIdentityService.createUserQuery().userId(userId).singleResult() == null) {
            User user = idmIdentityService.newUser(userId);
            user.setId(userId);
            user.setFirstName(userName);
            user.setLastName(userLastName);
            user.setEmail(userEmail);
            user.setPassword(password);
            idmIdentityService.saveUser(user);
        }
    }

    public void setMembership(String userId, String groupId) {
        if (!groupId.equals("Admin")) {
            boolean isAlreadyAMember = idmIdentityService.createUserQuery()
                    .userId(userId)
                    .memberOfGroup(groupId)
                    .count() > 0;
    
            if (!isAlreadyAMember) {
                idmIdentityService.createMembership(userId, groupId);
                System.out.println("Usuario " + userId + " asignado al grupo " + groupId);
            }
        } else {
            System.out.println("Grupo restringido");
        }
    }

    private void createGroupAdmin() {
        if (idmIdentityService.createGroupQuery().groupId("Admin").singleResult() == null) {
            Group adminGroup = idmIdentityService.newGroup("Admin");
            adminGroup.setName("Administradores de flowable");
            adminGroup.setType("security-role");
            idmIdentityService.saveGroup(adminGroup);
        }
    }

    private void createUserAdmin(String userId) {
        boolean isAlreadyAMember = idmIdentityService.createUserQuery()
                .userId(userId)
                .memberOfGroup("Admin")
                .count() > 0;

        if (!isAlreadyAMember) {
            idmIdentityService.createMembership(userId, "Admin");
        }
    }

    private void asignPrivilegesAdmin() {
        List<String> privileges = Arrays.asList(
            "access-idm",
            "access-admin",
            "access-modeler",
            "access-task",
            "access-rest-api");

        for (String name : privileges) {
            Privilege privilege = idmIdentityService.createPrivilegeQuery()
                    .privilegeName(name)
                    .singleResult();

            if (privilege == null) {
                throw new IllegalStateException("No existe el privilegio: " + name);
            }

            boolean alreadyAssigned = idmIdentityService.createPrivilegeQuery()
                    .privilegeId(privilege.getId())
                    .groupId("Admin")
                    .count() > 0;

            if (!alreadyAssigned) {
                idmIdentityService.addGroupPrivilegeMapping(privilege.getId(), "Admin");
            }
        }
    }
}
