package com.horsecontrol.eloquestre.models.settings;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Configurações globais (documento único, id "global"), editadas direto no banco.
 * paywallEnabled = contratantes precisam de assinatura ativa para publicar vagas.
 */
@Document(collection = "app_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppSettings {
    public static final String ID = "global";

    @Id
    private String id = ID;
    private boolean paywallEnabled;
}
