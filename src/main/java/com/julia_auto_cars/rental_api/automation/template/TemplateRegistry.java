package com.julia_auto_cars.rental_api.automation.template;

import java.util.List;
import java.util.Map;

/**
 * Static registry of every WhatsApp message template defined in the spec.
 * IDs and bodies match the project specification exactly.
 *
 * <p>Each template lists the variable paths it expects. The {@link MessageRenderer}
 * reads these paths from the flow context to build the final body.</p>
 */
public final class TemplateRegistry {

    public static final String BOOKING_ABANDONED   = "booking_abandoned";
    public static final String BOOKING_CONFIRMATION = "booking_confirmation";
    public static final String RENTAL_REMINDER     = "rental_reminder";
    public static final String UPSELL_OPTIONS      = "upsell_options";
    public static final String REVIEW_REQUEST      = "review_request";

    private static final Map<String, Template> TEMPLATES = Map.of(
        BOOKING_ABANDONED, new Template(
            BOOKING_ABANDONED,
            List.of(),
            """
            🚗 Bonjour {customer_name},

            Nous avons remarqué que vous avez commencé une réservation pour la {car_name} mais que celle-ci n'a pas encore été finalisée.

            📅 Dates sélectionnées : {start_date} → {end_date}

            Votre véhicule est toujours disponible pour le moment.

            👉 Reprendre ma réservation :
            {booking_link}

            Si vous avez la moindre question, répondez simplement à ce message ou contactez-nous au {agency_phone}.

            À bientôt,
            {agency_name}
            """
        ),
        BOOKING_CONFIRMATION, new Template(
            BOOKING_CONFIRMATION,
            List.of(),
            """
            ✅ Réservation confirmée

            Bonjour {customer_name},

            Merci pour votre confiance. Votre réservation a bien été enregistrée.

            🚗 Véhicule : {car_name}
            📅 Du : {start_date}
            📅 Au : {end_date}
            📍 Lieu de prise en charge : {pickup_location}

            Numéro de réservation :
            #{reservation_number}

            Notre équipe vous contactera si des informations complémentaires sont nécessaires.

            Nous avons hâte de vous accueillir.

            {agency_name}
            📞 {agency_phone}
            """
        ),
        RENTAL_REMINDER, new Template(
            RENTAL_REMINDER,
            List.of(),
            """
            ⏰ Rappel de réservation

            Bonjour {customer_name},

            Nous vous rappelons que votre location de véhicule débute demain.

            🚗 Véhicule : {car_name}
            📅 Date : {start_date}
            🕒 Heure : {pickup_time}
            📍 Lieu : {pickup_location}

            Merci de vous munir de :
            • Votre permis de conduire
            • Une pièce d'identité
            • Le moyen de paiement utilisé pour la réservation

            Pour toute question, notre équipe reste à votre disposition.

            À demain 👋

            {agency_name}
            """
        ),
        UPSELL_OPTIONS, new Template(
            UPSELL_OPTIONS,
            List.of(),
            """
            🚀 Personnalisez votre location

            Bonjour {customer_name},

            Souhaitez-vous profiter d'options supplémentaires pour rendre votre expérience encore plus confortable ?

            Options disponibles :

            ✅ Assurance tous risques
            ✅ Conducteur additionnel
            ✅ GPS
            ✅ Siège bébé
            ✅ Livraison du véhicule à votre hôtel ou à l'aéroport

            Répondez simplement à ce message avec les options souhaitées et notre équipe s'occupe du reste.

            {agency_name}
            """
        ),
        REVIEW_REQUEST, new Template(
            REVIEW_REQUEST,
            List.of("agency.review_link"),
            """
            ⭐ Merci pour votre confiance

            Bonjour {customer_name},

            Nous espérons que votre expérience avec {agency_name} a été à la hauteur de vos attentes.

            Votre avis nous aide à améliorer nos services et aide également d'autres voyageurs à faire leur choix.

            👉 Laisser un avis :
            {review_link}

            Cela ne prend qu'une minute et nous vous en remercions sincèrement.

            Au plaisir de vous accueillir à nouveau lors de votre prochain voyage 🚗

            {agency_name}
            """
        )
    );

    private TemplateRegistry() {}

    public static Template get(String id) {
        return TEMPLATES.get(id);
    }

    public static List<Template> all() {
        return List.copyOf(TEMPLATES.values());
    }

    public record Template(String id, List<String> variablePaths, String body) {}
}
