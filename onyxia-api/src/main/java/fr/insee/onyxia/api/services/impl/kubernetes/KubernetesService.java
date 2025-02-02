package fr.insee.onyxia.api.services.impl.kubernetes;

import fr.insee.onyxia.api.configuration.kubernetes.KubernetesClientProvider;
import fr.insee.onyxia.model.region.Region;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.SubjectBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class KubernetesService {

    @Autowired
    private KubernetesClientProvider kubernetesClientProvider;

    public void createNamespace(Region region, String namespaceId, Owner owner) {
        String username = owner.getId();
        if (owner.getType() == Owner.OwnerType.USER && region.getServices().getUsernamePrefix() != null) {
            username = region.getServices().getUsernamePrefix()+username;
        }
        // Label onyxia_owner is not resilient if the user has "namespace admin" role scoped to his namespace
        // as it this rolebinding allows him to modify onyxia_owner metadata
        KubernetesClient kubClient = kubernetesClientProvider.getRootClient(region);
        Namespace namespaceToCreate = kubClient.namespaces().create(new NamespaceBuilder().withNewMetadata().withName(namespaceId)
                .addToLabels("onyxia_owner", owner.getId()).endMetadata().build());

        RoleBinding bindingToCreate = kubClient.rbac().roleBindings().inNamespace(namespaceId).create(new RoleBindingBuilder()
                .withNewMetadata()
                .withLabels(Map.of("createdby","onyxia"))
                .withName("full_control_namespace").withNamespace(namespaceId).endMetadata()
                .withSubjects(new SubjectBuilder().withKind(getSubjectKind(owner)).withName(username)
                        .withApiGroup("rbac.authorization.k8s.io").withNamespace(namespaceId).build())
                .withNewRoleRef().withApiGroup("rbac.authorization.k8s.io").withKind("ClusterRole").withName("admin").endRoleRef().build());

        // TODO : create all in a single transaction if possible
    }

    public List<Namespace> getNamespaces(Region region, Owner owner) {
        KubernetesClient kubClient = kubernetesClientProvider.getRootClient(region);
        return kubClient.namespaces().withLabel("onyxia_owner",owner.getId()).list().getItems();
    }

    private String getSubjectKind(Owner owner) {
        if (owner.getType() == Owner.OwnerType.GROUP) {
            return "Group";
        } else if (owner.getType() == Owner.OwnerType.USER) {
            return "User";
        }
        throw new IllegalArgumentException("Owner type must be one of : USER, GROUP");
    }

    public static class Owner {
        private String id;
        private OwnerType type;

        public static enum OwnerType {
            USER, GROUP;
        }

        public OwnerType getType() {
            return type;
        }

        public void setType(OwnerType type) {
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

}