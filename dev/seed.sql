-- Development data: test accounts, properties, bookings, statements and transactions.
-- Runs once on an empty development database (see DevSeedRunner); never in production.
-- All names, addresses, numbers and amounts are invented.
--
-- Every account: password "logis-dev-passwort", TOTP secret LOGISDEVLOGISDEVLOGISDEVLOGISDEV.

DO $seed$
DECLARE
  pw   CONSTANT TEXT := '{bcrypt}$2a$10$Hf6UrBDHatYnJl8lToDIMuOd4B7/UbNZnMPERBH0BuAxG9APT2RbG';
  totp CONSTANT TEXT := 'TXWTwhX0f9fDqp3MsxQy0LG4+v9SkhTa0JpQ/F+FhgVulP4gRE8Jv/1cPic++cjr6oR7cjxbGN5FeBFc';
  u_admin BIGINT; u_anna BIGINT; u_bert BIGINT; u_carla BIGINT; u_dieter BIGINT; u_erik BIGINT;
  le_anna BIGINT; le_gbr BIGINT; le_bert BIGINT; le_carla BIGINT; le_dieter BIGINT;
  p_seeufer BIGINT; p_duene BIGINT; p_linden BIGINT; p_park BIGINT;
  seeufer BIGINT; whg1 BIGINT; whg2 BIGINT; eg_links BIGINT; eg_rechts BIGINT; og1 BIGINT; dg BIGINT; garage BIGINT; park BIGINT;
  a_giro BIGINT; a_gbr BIGINT; a_tages BIGINT; a_kasse BIGINT;
  ag_fewo BIGINT; ag_holidu BIGINT; ag_airbnb BIGINT; ag_booking BIGINT; ag_direkt BIGINT; ag_vvv BIGINT;
  c_gehalt BIGINT; c_miete BIGINT; c_kapital BIGINT; c_wohnen BIGINT; c_lebensmittel BIGINT; c_mobil BIGINT;
  c_vers BIGINT; c_gebuehren BIGINT; c_grundsteuer BIGINT; c_neben BIGINT; c_instand BIGINT; c_kraftstoff BIGINT; c_sonst BIGINT;
  b BIGINT; st BIGINT; d DATE; n INT; r DOUBLE PRECISION; i INT; m DATE; u BIGINT; ag BIGINT; nightly NUMERIC;
  counter INT := 0; ref TEXT; first TEXT; last TEXT; city TEXT; total NUMERIC; tid UUID;
  firsts CONSTANT TEXT[] := ARRAY['Lena','Jonas','Mia','Paul','Emma','Felix','Hanna','Lukas','Clara','Noah','Lea','Ben','Sophie','Elias','Marie','Finn','Ida','Leon','Nora','Theo'];
  lasts  CONSTANT TEXT[] := ARRAY['Beispiel','Muster','Probe','Testmann','Sommerfeld','Seemann','Wellenbrink','Kieselbach','Strandmann','Dünenberg','Möwenstein','Leuchtwald','Brandung','Muschelmann','Gezeitner','Fischerhof'];
  cities CONSTANT TEXT[] := ARRAY['Musterstadt','Beispielstadt','Probedorf','Testhausen','Seehausen','Neudorf'];
  units BIGINT[];
BEGIN
  IF EXISTS (SELECT 1 FROM core.users WHERE lower(username) = 'anna') THEN
    RAISE NOTICE 'Development data already present';
    RETURN;
  END IF;
  PERFORM setseed(0.42);

  -- Users ------------------------------------------------------------------------------------------
  SELECT id INTO u_admin FROM core.users WHERE lower(username) = 'admin';
  IF u_admin IS NULL THEN
    INSERT INTO core.users (username, display_name, role, password_hash, totp_secret_enc)
      VALUES ('admin', 'Administrator', 'ADMIN', pw, totp) RETURNING id INTO u_admin;
  ELSE
    UPDATE core.users SET password_hash = pw, totp_secret_enc = totp, password_change_required = false WHERE id = u_admin;
  END IF;
  INSERT INTO core.users (username, display_name, role, password_hash, totp_secret_enc)
    VALUES ('anna', 'Anna Vermieterin', 'USER', pw, totp) RETURNING id INTO u_anna;
  INSERT INTO core.users (username, display_name, role, password_hash, totp_secret_enc)
    VALUES ('bert', 'Bert Hausmeister', 'USER', pw, totp) RETURNING id INTO u_bert;
  INSERT INTO core.users (username, display_name, role, password_hash, totp_secret_enc)
    VALUES ('carla', 'Carla Steuerberaterin', 'USER', pw, totp) RETURNING id INTO u_carla;
  -- New user: must set up two-factor sign-in and change the start password.
  INSERT INTO core.users (username, display_name, role, password_hash, password_change_required)
    VALUES ('dieter', 'Dieter Neuling', 'USER', pw, true) RETURNING id INTO u_dieter;
  INSERT INTO core.users (username, display_name, role, password_hash, totp_secret_enc, enabled)
    VALUES ('erik', 'Erik Ehemalig', 'USER', pw, totp, false) RETURNING id INTO u_erik;

  -- Legal entities -----------------------------------------------------------------------------------
  INSERT INTO core.legal_entities (name, kind, manager_user_id, personal) VALUES ('Anna privat', 'PRIVATE_PERSON', u_anna, true) RETURNING id INTO le_anna;
  INSERT INTO core.legal_entities (name, kind, tax_number, manager_user_id) VALUES ('Muster GbR', 'GBR', '12/345/67890', u_anna) RETURNING id INTO le_gbr;
  INSERT INTO core.legal_entities (name, kind, manager_user_id, personal) VALUES ('Bert privat', 'PRIVATE_PERSON', u_bert, true) RETURNING id INTO le_bert;
  INSERT INTO core.legal_entities (name, kind, manager_user_id, personal) VALUES ('Carla privat', 'PRIVATE_PERSON', u_carla, true) RETURNING id INTO le_carla;
  INSERT INTO core.legal_entities (name, kind, manager_user_id, personal) VALUES ('Dieter privat', 'PRIVATE_PERSON', u_dieter, true) RETURNING id INTO le_dieter;

  -- Properties and units -------------------------------------------------------------------------
  INSERT INTO core.properties (legal_entity_id, name, street, postal_code, city, multi_unit)
    VALUES (le_gbr, 'Haus Seeufer', 'Uferweg 3', '12345', 'Musterstadt', false) RETURNING id INTO p_seeufer;
  INSERT INTO core.units (property_id, name, usage) VALUES (p_seeufer, 'Haus Seeufer', 'VACATION_RENTAL') RETURNING id INTO seeufer;
  INSERT INTO core.properties (legal_entity_id, name, street, postal_code, city, multi_unit)
    VALUES (le_gbr, 'Strandhaus Dünenblick', 'Dünenstraße 7', '12346', 'Seehausen', true) RETURNING id INTO p_duene;
  INSERT INTO core.units (property_id, name, usage, sort_order) VALUES (p_duene, 'Whg. 1', 'VACATION_RENTAL', 1) RETURNING id INTO whg1;
  INSERT INTO core.units (property_id, name, usage, sort_order) VALUES (p_duene, 'Whg. 2', 'VACATION_RENTAL', 2) RETURNING id INTO whg2;
  INSERT INTO core.properties (legal_entity_id, name, street, postal_code, city, multi_unit)
    VALUES (le_anna, 'Mehrfamilienhaus Lindenstraße', 'Lindenstraße 12', '12347', 'Beispielstadt', true) RETURNING id INTO p_linden;
  INSERT INTO core.units (property_id, name, usage, sort_order) VALUES (p_linden, 'EG links', 'LONG_TERM_RENTAL', 1) RETURNING id INTO eg_links;
  INSERT INTO core.units (property_id, name, usage, sort_order) VALUES (p_linden, 'EG rechts', 'LONG_TERM_RENTAL', 2) RETURNING id INTO eg_rechts;
  INSERT INTO core.units (property_id, name, usage, sort_order) VALUES (p_linden, '1. OG', 'LONG_TERM_RENTAL', 3) RETURNING id INTO og1;
  INSERT INTO core.units (property_id, name, usage, sort_order) VALUES (p_linden, 'DG', 'LONG_TERM_RENTAL', 4) RETURNING id INTO dg;
  INSERT INTO core.units (property_id, name, usage, sort_order) VALUES (p_linden, 'Garage', 'OTHER', 5) RETURNING id INTO garage;
  INSERT INTO core.properties (legal_entity_id, name, street, postal_code, city, multi_unit)
    VALUES (le_anna, 'Eigentumswohnung Parkallee', 'Parkallee 5', '12348', 'Musterstadt', false) RETURNING id INTO p_park;
  INSERT INTO core.units (property_id, name, usage) VALUES (p_park, 'Eigentumswohnung Parkallee', 'OWN_USE') RETURNING id INTO park;

  -- Anna owns everything; Bert edits one holiday flat, Carla reads Haus Seeufer and the GbR account.
  INSERT INTO core.object_shares (object_type, object_id, user_id, role) VALUES
    ('property', p_seeufer, u_anna, 'OWNER'), ('property', p_duene, u_anna, 'OWNER'),
    ('property', p_linden, u_anna, 'OWNER'), ('property', p_park, u_anna, 'OWNER'),
    ('unit', whg1, u_bert, 'EDITOR'), ('property', p_seeufer, u_carla, 'VIEWER');

  -- Accounts ---------------------------------------------------------------------------------------
  INSERT INTO core.accounts (legal_entity_id, name, kind, iban, opening_balance) VALUES (le_anna, 'Girokonto', 'CHECKING', 'DE89370400440532013000', 2450.00) RETURNING id INTO a_giro;
  INSERT INTO core.accounts (legal_entity_id, name, kind, opening_balance) VALUES (le_anna, 'Tagesgeld', 'SAVINGS', 8000.00) RETURNING id INTO a_tages;
  INSERT INTO core.accounts (legal_entity_id, name, kind, opening_balance) VALUES (le_anna, 'Barkasse', 'CASH', 150.00) RETURNING id INTO a_kasse;
  INSERT INTO core.accounts (legal_entity_id, name, kind, iban, opening_balance) VALUES (le_gbr, 'GbR-Konto', 'CHECKING', 'DE02120300000000202051', 5200.00) RETURNING id INTO a_gbr;
  INSERT INTO core.object_shares (object_type, object_id, user_id, role) VALUES
    ('account', a_giro, u_anna, 'OWNER'), ('account', a_tages, u_anna, 'OWNER'), ('account', a_kasse, u_anna, 'OWNER'),
    ('account', a_gbr, u_anna, 'OWNER'), ('account', a_gbr, u_carla, 'VIEWER');

  SELECT id INTO c_gehalt FROM core.categories WHERE name = 'Gehalt' AND module_key IS NULL;
  SELECT id INTO c_miete FROM core.categories WHERE name = 'Mieteinnahmen' AND module_key IS NULL;
  SELECT id INTO c_kapital FROM core.categories WHERE name = 'Kapitalerträge' AND module_key IS NULL;
  SELECT id INTO c_wohnen FROM core.categories WHERE name = 'Wohnen' AND module_key IS NULL;
  SELECT id INTO c_lebensmittel FROM core.categories WHERE name = 'Lebensmittel' AND module_key IS NULL;
  SELECT id INTO c_mobil FROM core.categories WHERE name = 'Mobilität' AND module_key IS NULL;
  SELECT id INTO c_kraftstoff FROM core.categories WHERE name = 'Kraftstoff' AND module_key IS NULL;
  SELECT id INTO c_vers FROM core.categories WHERE name = 'Versicherungen' AND module_key IS NULL;
  SELECT id INTO c_gebuehren FROM core.categories WHERE name = 'Gebühren' AND module_key IS NULL;
  SELECT id INTO c_grundsteuer FROM core.categories WHERE name = 'Grundsteuer' AND module_key IS NULL;
  SELECT id INTO c_neben FROM core.categories WHERE name = 'Nebenkosten' AND module_key IS NULL;
  SELECT id INTO c_instand FROM core.categories WHERE name = 'Instandhaltung' AND module_key IS NULL;
  SELECT id INTO c_sonst FROM core.categories WHERE name = 'Sonstige Ausgaben' AND module_key IS NULL;

  -- Agencies ---------------------------------------------------------------------------------------
  INSERT INTO vacationrental.agencies (name) VALUES ('Fewo & Meer') RETURNING id INTO ag_fewo;
  INSERT INTO vacationrental.agencies (name) VALUES ('Holidu') RETURNING id INTO ag_holidu;
  INSERT INTO vacationrental.agencies (name) VALUES ('Airbnb') RETURNING id INTO ag_airbnb;
  INSERT INTO vacationrental.agencies (name) VALUES ('Booking.com') RETURNING id INTO ag_booking;
  INSERT INTO vacationrental.agencies (name) VALUES ('Direkt') RETURNING id INTO ag_direkt;
  INSERT INTO vacationrental.agencies (name) VALUES ('VVV Nordhorn') RETURNING id INTO ag_vvv;
  INSERT INTO vacationrental.agency_aliases (agency_id, alias) VALUES
    (ag_fewo, 'Fewo & Meer'), (ag_holidu, 'Holidu'), (ag_airbnb, 'Airbnb'), (ag_booking, 'Booking.com'),
    (ag_direkt, 'Direkt'), (ag_vvv, 'VVV Nordhorn'), (ag_vvv, 'VVV-Stadt- und Citymarketing Nordhorn e.V.');
  INSERT INTO vacationrental.unit_mappings (owner_user_id, agency_id, property_name, unit_id) VALUES
    (u_anna, ag_fewo, 'Haus Seeufer - Nordhorn', seeufer), (u_anna, ag_holidu, 'Ferienhaus Seeufer', seeufer),
    (u_anna, ag_vvv, 'Ferienhaus Seeufer', whg1);

  -- Bookings imported from mails; their sources and highlights are derived at startup ---------------
  INSERT INTO vacationrental.bookings (unit_id, arrival, departure, external_ref, adults, children, expected_rent,
      guest_name, guest_street, guest_postal_code, guest_city, guest_country, guest_phone, guest_email, agency_id, review_state, created_at)
    VALUES (seeufer, '2026-11-06', '2026-11-08', '250905', 4, 0, 248.58, 'Erika Musterfrau', 'Musterweg 1', '12345',
      'Musterstadt', 'Deutschland', '0151 0000001', 'erika@example.org', ag_fewo, 'NEW', '2026-11-06 11:15+00') RETURNING id INTO b;
  INSERT INTO core.documents (owner_type, owner_id, file_name, content_type, size_bytes, content, created_at) VALUES
    ('vacationrental.booking', b, 'Fewo & Meer Buchungsbestätigung 250905.eml', 'message/rfc822', 1, convert_to($eml$From: "Fewo & Meer" <buchung@fewoundmeer.de>
To: vermieter@example.org
Subject: Neue Buchung für Haus Seeufer - Nordhorn
Date: Fri, 06 Nov 2026 12:15:00 +0100
Message-ID: <fewo-250905@fewoundmeer.de>
MIME-Version: 1.0
Content-Type: text/html; charset=UTF-8
Content-Transfer-Encoding: 8bit

<html><body>
<p>Sehr geehrte(r) Muster GbR,</p>
<p>wir informieren Sie hiermit über den Eingang einer Buchung für Ihr Objekt Haus Seeufer - Nordhorn vom 06.11.2026 bis 08.11.2026.</p>
<table>
<tr><td><b>Buchungsnummer</b></td><td>250905</td></tr>
<tr><td><b>Anzahl Gäste</b></td><td>4 Erwachsene</td></tr>
<tr><td><b>Anreise</b></td><td>06.11.2026</td></tr>
<tr><td><b>Abreise</b></td><td>08.11.2026</td></tr>
<tr><td><b>Mietpreis</b></td><td>248,58 EUR</td></tr>
</table>
<p><b>Informationen zum Gast:</b></p>
<table>
<tr><td><b>Name</b></td><td>Erika Musterfrau</td></tr>
<tr><td><b>Adresse</b></td><td>Musterweg 1<br>12345 Musterstadt<br>Deutschland</td></tr>
<tr><td><b>Telefon</b></td><td>0151 0000001</td></tr>
<tr><td><b>Telefon (mobil)</b></td><td>0151 0000001</td></tr>
<tr><td><b>Email</b></td><td>erika@example.org</td></tr>
</table>
<p>Mit freundlichen Grüßen<br>P. Beispiel</p>
</body></html>
$eml$, 'UTF8'), '2026-11-06 11:15+00'),
    ('vacationrental.booking', b, 'Fewo & Meer Last-Minute 250905.eml', 'message/rfc822', 1, convert_to($eml$From: "Fewo & Meer" <buchung@fewoundmeer.de>
To: vermieter@example.org
Subject: Last-Minute Buchung 250905, Haus Seeufer - Nordhorn, vom 06.11.2026 bis 08.11.2026
Date: Fri, 06 Nov 2026 12:20:00 +0100
Message-ID: <fewo-lm-250905@fewoundmeer.de>
MIME-Version: 1.0
Content-Type: text/plain; charset=UTF-8
Content-Transfer-Encoding: 8bit

Sehr geehrte Damen und Herren,

wir möchten Sie über eine kurzfristige Anreise informieren.

Objekt: Haus Seeufer - Nordhorn
Gast-Name: Musterfrau Erika
Zeitraum: 06.11.2026 - 08.11.2026
Personenzahl: 4 Erwachsene/ 0 Kinder
Haustiere: 0
Telefon-Nr.:
Mobil-Nr.: +49 151 0000001

Viele Grüße

L. Beispiel

Fewo & Meer e.K.
Ferienhausvermittlung nach Maß
$eml$, 'UTF8'), '2026-11-06 12:00+00');

  -- The guest name was corrected by hand; a change mail with a new departure awaits review.
  INSERT INTO vacationrental.bookings (unit_id, arrival, departure, external_ref, adults, children, expected_rent,
      guest_name, guest_street, guest_postal_code, guest_city, guest_country, guest_phone, guest_email, agency_id,
      review_state, proposal, created_at)
    VALUES (seeufer, '2026-12-23', '2026-12-27', '250903', 2, 1, 559.00, 'Jonas Beispiel-Lang', 'Probeweg 6', '34497',
      'Probestadt', 'Deutschland', '0172 0000002', 'jonas@example.org', ag_fewo, 'CHANGE',
      '{"type":"CHANGE","departure":"2026-12-28"}'::jsonb, '2026-11-02 10:00+00') RETURNING id INTO b;
  INSERT INTO core.documents (owner_type, owner_id, file_name, content_type, size_bytes, content, created_at) VALUES
    ('vacationrental.booking', b, 'Fewo & Meer Buchungsbestätigung 250903.eml', 'message/rfc822', 1, convert_to($eml$From: "Fewo & Meer" <buchung@fewoundmeer.de>
To: vermieter@example.org
Subject: Neue Buchung für Haus Seeufer - Nordhorn
Date: Sat, 07 Nov 2026 09:00:00 +0100
Message-ID: <fewo-250903@fewoundmeer.de>
MIME-Version: 1.0
Content-Type: text/html; charset=UTF-8
Content-Transfer-Encoding: 8bit

<html><body>
<p>Sehr geehrte(r) Muster GbR,</p>
<p>wir informieren Sie hiermit über den Eingang einer Buchung für Ihr Objekt Haus Seeufer - Nordhorn vom 23.12.2026 bis 27.12.2026.</p>
<table>
<tr><td>Buchungsnummer</td><td>250903</td></tr>
<tr><td>Anzahl Gäste</td><td>2 Erwachsene, 1 Kind</td></tr>
<tr><td>Anreise</td><td>23.12.2026</td></tr>
<tr><td>Abreise</td><td>27.12.2026</td></tr>
<tr><td>Mietpreis</td><td>559,00 EUR</td></tr>
</table>
<p>Informationen zum Gast:</p>
<table>
<tr><td>Name</td><td>Jonas Beispiel</td></tr>
<tr><td>Adresse</td><td>Probeweg 6<br>34497 Probestadt<br>Deutschland</td></tr>
<tr><td>Telefon</td><td></td></tr>
<tr><td>Telefon (mobil)</td><td>0172 0000002</td></tr>
<tr><td>Email</td><td>jonas@example.org</td></tr>
</table>
</body></html>
$eml$, 'UTF8'), '2026-11-02 10:00+00'),
    ('vacationrental.booking', b, 'Fewo & Meer Änderung 250903.eml', 'message/rfc822', 1, convert_to($eml$From: "Fewo & Meer" <buchung@fewoundmeer.de>
To: vermieter@example.org
Subject: Neue Buchung für Haus Seeufer - Nordhorn
Date: Mon, 09 Nov 2026 09:00:00 +0100
Message-ID: <dev-change-250903@example.org>
MIME-Version: 1.0
Content-Type: text/html; charset=UTF-8
Content-Transfer-Encoding: 8bit

<html><body>
<p>Sehr geehrte(r) Muster GbR,</p>
<p>wir informieren Sie hiermit über den Eingang einer Buchung für Ihr Objekt Haus Seeufer - Nordhorn vom 23.12.2026 bis 28.12.2026.</p>
<table>
<tr><td>Buchungsnummer</td><td>250903</td></tr>
<tr><td>Anzahl Gäste</td><td>2 Erwachsene, 1 Kind</td></tr>
<tr><td>Anreise</td><td>23.12.2026</td></tr>
<tr><td>Abreise</td><td>28.12.2026</td></tr>
<tr><td>Mietpreis</td><td>559,00 EUR</td></tr>
</table>
<p>Informationen zum Gast:</p>
<table>
<tr><td>Name</td><td>Jonas Beispiel</td></tr>
<tr><td>Adresse</td><td>Probeweg 6<br>34497 Probestadt<br>Deutschland</td></tr>
<tr><td>Telefon</td><td></td></tr>
<tr><td>Telefon (mobil)</td><td>0172 0000002</td></tr>
<tr><td>Email</td><td>jonas@example.org</td></tr>
</table>
</body></html>
$eml$, 'UTF8'), '2026-11-09 08:00+00');

  INSERT INTO vacationrental.bookings (unit_id, arrival, departure, external_ref, adults, children, expected_rent,
      guest_name, guest_street, guest_postal_code, guest_city, guest_country, guest_phone, guest_email, agency_id, created_at)
    VALUES (seeufer, '2026-12-04', '2026-12-06', '3e12f345gh', 2, 0, 309.08, 'Anna Probe', 'Ackerweg 2', '45701',
      'Beispielstadt', 'Deutschland', '004915200000003', 'gast-0003@guest.holidu.com', ag_holidu, '2026-10-31 13:00+00') RETURNING id INTO b;
  INSERT INTO core.documents (owner_type, owner_id, file_name, content_type, size_bytes, content, created_at) VALUES
    ('vacationrental.booking', b, 'Holidu Buchungsbestätigung 3e12f345gh.eml', 'message/rfc822', 1, convert_to($eml$From: "Holidu Buchungsservice" <buchungsservice@ds-destinationsolutions.com>
To: vermieter@example.org
Subject: Buchungsmitteilung 3e12f345gh - Ferienhaus Seeufer
Date: Sat, 31 Oct 2026 14:00:00 +0100
Message-ID: <ds-3e12f345gh@ds-destinationsolutions.com>
MIME-Version: 1.0
Content-Type: text/plain; charset=UTF-8
Content-Transfer-Encoding: 8bit

Sie haben eine neue Buchung über "Destination Solutions" erhalten. Achten Sie auf den Gesamtpreis in Höhe von 309,08 EUR der dem Gast für diese Buchung zu berechnen ist.

München, 31.10.2026
Vermittler (Stornierung/Änderung): Holidu GmbH in Kooperation mit "DS Destination Solutions"
                    Riesstraße 24, 80992 München

Buchungsmitteilung (Buchungsnummer: 3e12f345gh)
Buchungsart: fremd, Buchungssystem: im-web
Partnerwebsite: HRS holidays / holidu.de

Ihre Unterkunft: Ferienhaus Seeufer (Kategorie: Ferienhaus Seeufer, Wohneinheit: Ferienhaus Seeufer) wurde wie folgt gebucht:

Reisezeit:          Anreise: 04.12.2026 Abreise: 06.12.2026 (2 Übernachtungen)

Gast:               Anna Probe (K.-Nr.: K00b44c55ee6)
                    Ackerweg 2, 45701 Beispielstadt, Deutschland
                    Tel.: 004915200000003
                    E-Mail: gast-0003@guest.holidu.com

Reisende:           2 Erwachsene(r)

Mietpreis:          160,00 EUR (2*80,00 EUR, enth. 10,47 EUR (7%) MwSt.)
Endreinigung:       111,00 EUR (1*111,00 EUR, enth. 0,00 EUR (0%) MwSt.)

+--------------------------------------------------------------------------------+
|REISEPREIS:        309,08 EUR (enth. 12,96 EUR zu (7.00%) MwSt.,                |
+--------------------------------------------------------------------------------+

Anzahlung:          61,82 EUR
Restzahlung:        247,26 EUR

Mit freundlichen Grüßen
Holidu GmbH in Kooperation mit "DS Destination Solutions"
$eml$, 'UTF8'), '2026-10-31 13:00+00');

  INSERT INTO vacationrental.bookings (unit_id, arrival, departure, external_ref, adults, children, expected_rent,
      guest_name, guest_street, guest_postal_code, guest_city, guest_country, guest_phone, guest_email, agency_id, created_at)
    VALUES (whg1, '2027-05-15', '2027-05-18', '3e98f765kl', 2, 2, 447.00, 'Max Probe', 'Testweg 19a', '46242',
      'Beispielstadt', 'Deutschland', '004917400000005', 'max@example.org', ag_vvv, '2026-10-29 10:50+00') RETURNING id INTO b;
  INSERT INTO core.documents (owner_type, owner_id, file_name, content_type, size_bytes, content, created_at) VALUES
    ('vacationrental.booking', b, 'VVV Nordhorn Buchungsbestätigung 3e98f765kl.eml', 'message/rfc822', 1, convert_to($eml$From: "VVV Buchungsservice" <buchungsservice@ds-destinationsolutions.com>
To: vermieter@example.org
Subject: Buchungsmitteilung 3e98f765kl - Ferienhaus Seeufer
Date: Thu, 29 Oct 2026 11:50:00 +0100
Message-ID: <ds-3e98f765kl@ds-destinationsolutions.com>
MIME-Version: 1.0
Content-Type: text/plain; charset=UTF-8
Content-Transfer-Encoding: 8bit

Sie haben eine neue Buchung über "Destination Solutions" erhalten.
Achten Sie auf den Gesamtpreis in Höhe von 447,00 EUR der dem Gast für
diese Buchung zu berechnen ist.

                                                             Nordhorn, 29.10.2026
Vermittler (Stornierung/Änderung): VVV-Stadt- und Citymarketing Nordhorn e.V.
                   Firnhaberstraße 17, 48529 Nordhorn

Datenhalter:        VVV-Stadt- und Citymarketing Nordhorn e.V.
                   Firnhaberstraße 17, 48529 Nordhorn

Buchungsmitteilung (Buchungsnummer: 3e98f765kl)
Buchungsart: fremd, Buchungssystem: im-web, Partnerwebsite: IBE NG

Ihre Unterkunft: Ferienhaus Seeufer
(Kategorie: Ferienhaus Seeufer, Wohneinheit: Ferienhaus Seeufer) wurde wie folgt
gebucht:

Reisezeit:          Anreise: 15.05.2027 Abreise: 18.05.2027 (3 Übernachtungen)

Gast:               Max Probe (K.-Nr.: K00b11c22dd3)
                   Testweg 19a, 46242 Beispielstadt, Deutschland
                   Tel.: 004917400000005
                   E-Mail: max@example.org

Reisende:           2 Erwachsene(r), 2 Kind(er) im Alter von 6 / 9 Jahr(en)

Mietpreis:          336,00 EUR (3*112,00 EUR, enth. 21,98 EUR (7%) MwSt.)
Endreinigung:       111,00 EUR (1*111,00 EUR, enth. 0,00 EUR (0%) MwSt.)

+--------------------------------------------------------------------------------+
|REISEPREIS:        447,00 EUR (enth. 21,98 EUR zu (7.00%) MwSt.,                |
|                   0,00 EUR zu (0.00%) MwSt.)                                   |
+--------------------------------------------------------------------------------+

Anzahlung:          89,40 EUR
Restzahlung:        357,60 EUR

Mit freundlichen Grüßen
VVV-Stadt- und Citymarketing Nordhorn e.V
$eml$, 'UTF8'), '2026-10-29 10:50+00'),
    ('vacationrental.booking', b, 'Fewo & Meer Blockung 250904.eml', 'message/rfc822', 1, convert_to($eml$From: "fewoscout.com" <info@fewoscout.com>
To: vermieter@example.org
Cc: uebernachtungen@example.org
Subject: Neue Blockung für Haus Seeufer - Nordhorn,250904, vom 15.05.2027 bis 18.05.2027 durch den VVV Nordhorn
Date: Thu, 29 Oct 2026 11:55:30 +0100
Message-ID: <fewo-block-250904@fewoscout.com>
MIME-Version: 1.0
Content-Type: text/plain; charset=UTF-8
Content-Transfer-Encoding: 8bit

Neue Blockung für Haus Seeufer - Nordhorn,250904, vom 15.05.2027 bis 18.05.2027 durch den VVV Nordhorn

Sehr geehrter Eigentümer,

das Stadtmarketing hat Ihr Objekt Haus Seeufer - Nordhorn für den Zeitraum vom 15.05.2027 bis 18.05.2027 für Sie geblockt.

Viele Grüße

C. Beispiel

Fewo & Meer e.K.

Ferienhausvermittlung nach Maß
$eml$, 'UTF8'), '2026-10-29 11:00+00');
  UPDATE core.documents SET size_bytes = length(content) WHERE owner_type = 'vacationrental.booking';

  -- Bookings from January 2025 to June 2027, recorded by hand: longer stays in summer, fewer in winter --
  units := ARRAY[seeufer, whg1, whg2];
  FOREACH u IN ARRAY units LOOP
    d := DATE '2025-01-03';
    WHILE d < DATE '2027-06-30' LOOP
      d := d + floor(random() * 6)::int;
      IF extract(month FROM d) IN (1, 2, 11) AND random() < 0.5 THEN
        d := d + 7;
        CONTINUE;
      END IF;
      n := CASE WHEN extract(month FROM d) IN (6, 7, 8) THEN 5 + floor(random() * 6)::int
                WHEN extract(month FROM d) IN (4, 5, 9, 10, 12) THEN 3 + floor(random() * 4)::int
                ELSE 2 + floor(random() * 3)::int END;
      IF NOT EXISTS (SELECT 1 FROM vacationrental.bookings x WHERE x.unit_id = u AND NOT x.cancelled
                     AND daterange(x.arrival, x.departure) && daterange(d, d + n)) THEN
        counter := counter + 1;
        r := random();
        ag := CASE WHEN r < 0.35 THEN ag_fewo WHEN r < 0.55 THEN ag_holidu WHEN r < 0.75 THEN ag_airbnb
                   WHEN r < 0.87 THEN ag_booking ELSE ag_direkt END;
        ref := CASE ag WHEN ag_fewo THEN (241000 + counter)::text
                       WHEN ag_holidu THEN substr(md5(random()::text), 1, 10)
                       WHEN ag_airbnb THEN 'HM' || upper(substr(md5(random()::text), 1, 8))
                       WHEN ag_booking THEN (4100000000 + counter * 7)::text
                       ELSE NULL END;
        nightly := CASE WHEN extract(month FROM d) IN (6, 7, 8) THEN 125 WHEN extract(month FROM d) IN (4, 5, 9, 10, 12) THEN 95 ELSE 78 END
                   * CASE WHEN u = seeufer THEN 1.0 ELSE 0.85 END;
        first := firsts[1 + floor(random() * array_length(firsts, 1))::int];
        last := lasts[1 + floor(random() * array_length(lasts, 1))::int];
        city := cities[1 + floor(random() * array_length(cities, 1))::int];
        INSERT INTO vacationrental.bookings (unit_id, arrival, departure, external_ref, adults, children, pets, expected_rent,
            guest_name, guest_street, guest_postal_code, guest_city, guest_country, guest_phone, guest_email, agency_id,
            cancelled, created_at)
          VALUES (u, d, d + n, ref, 1 + floor(random() * 4)::int, floor(random() * 3)::int,
            CASE WHEN random() < 0.1 THEN 1 ELSE 0 END, round(nightly * n + 60, 2),
            first || ' ' || last, 'Beispielweg ' || (1 + floor(random() * 80)::int), lpad((10000 + floor(random() * 89999)::int)::text, 5, '0'),
            city, 'Deutschland', '0170 ' || lpad(counter::text, 7, '0'),
            lower(translate(first || '.' || last, 'äöüÄÖÜ', 'aouAOU')) || '@example.org', ag,
            random() < 0.05, (d - (14 + floor(random() * 120)::int))::timestamptz);
      END IF;
      d := d + n;
    END LOOP;
  END LOOP;

  -- Statements of the agencies that settle monthly, up to October 2026 -------------------------------
  FOREACH u IN ARRAY units LOOP
    FOREACH ag IN ARRAY ARRAY[ag_fewo, ag_holidu, ag_booking] LOOP
      m := DATE '2025-01-01';
      WHILE m < DATE '2026-11-01' LOOP
        IF EXISTS (SELECT 1 FROM vacationrental.bookings x WHERE x.unit_id = u AND x.agency_id = ag AND NOT x.cancelled
                   AND x.departure >= m AND x.departure < (m + interval '1 month')::date) THEN
          INSERT INTO vacationrental.statements (unit_id, provider, period_from, period_to, statement_date)
            VALUES (u, (SELECT name FROM vacationrental.agencies WHERE id = ag), m, (m + interval '1 month - 1 day')::date,
                    (m + interval '1 month 4 days')::date) RETURNING id INTO st;
          INSERT INTO vacationrental.statement_lines (booking_id, statement_id, gross, commission, cleaning)
            SELECT x.id, st, x.expected_rent,
                   round(x.expected_rent * CASE ag WHEN ag_fewo THEN 0.15 WHEN ag_holidu THEN 0.18 ELSE 0.17 END, 2), 60.00
            FROM vacationrental.bookings x
            WHERE x.unit_id = u AND x.agency_id = ag AND NOT x.cancelled
              AND x.departure >= m AND x.departure < (m + interval '1 month')::date;
          -- The payout arrives on the GbR account.
          SELECT sum(gross - commission - cleaning) INTO total FROM vacationrental.statement_lines WHERE statement_id = st;
          INSERT INTO core.transactions (account_id, booking_date, amount, category_id, counterparty, purpose, unit_id, created_by, updated_by)
            VALUES (a_gbr, (m + interval '1 month 6 days')::date, total, c_miete,
                    (SELECT name FROM vacationrental.agencies WHERE id = ag), 'Auszahlung ' || to_char(m, 'MM/YYYY'), u, u_anna, u_anna);
        END IF;
        m := (m + interval '1 month')::date;
      END LOOP;
    END LOOP;
  END LOOP;

  -- Direct and Airbnb guests pay on arrival.
  INSERT INTO core.transactions (account_id, booking_date, amount, category_id, counterparty, purpose, unit_id, created_by, updated_by)
    SELECT a_gbr, x.arrival, x.expected_rent, c_miete, x.guest_name,
           coalesce('Buchung ' || x.external_ref, 'Ferienwohnung ' || to_char(x.arrival, 'DD.MM.YYYY')), x.unit_id, u_anna, u_anna
    FROM vacationrental.bookings x
    WHERE x.agency_id IN (ag_airbnb, ag_direkt) AND NOT x.cancelled AND x.arrival < DATE '2026-11-15';

  -- Monthly money flows from January 2025 to November 2026 ------------------------------------------
  m := DATE '2025-01-01';
  WHILE m <= DATE '2026-11-01' LOOP
    -- GbR: utilities per holiday home, account fee, property tax each quarter, insurance in January.
    INSERT INTO core.transactions (account_id, booking_date, amount, category_id, counterparty, purpose, property_id, created_by, updated_by) VALUES
      (a_gbr, m + 2, -94.50, c_neben, 'Stadtwerke Musterstadt', 'Abschlag Strom/Wasser Haus Seeufer', p_seeufer, u_anna, u_anna),
      (a_gbr, m + 2, -132.00, c_neben, 'Stadtwerke Seehausen', 'Abschlag Strom/Wasser Dünenblick', p_duene, u_anna, u_anna),
      (a_gbr, m + 9, -38.00, c_neben, 'Reinigungsdienst Blitzblank', 'Wäschepauschale', p_duene, u_anna, u_anna);
    INSERT INTO core.transactions (account_id, booking_date, amount, category_id, counterparty, purpose, created_by, updated_by)
      VALUES (a_gbr, (m + interval '1 month - 1 day')::date, -9.90, c_gebuehren, 'Beispielbank', 'Kontoführung', u_anna, u_anna);
    IF extract(month FROM m) IN (2, 5, 8, 11) THEN
      INSERT INTO core.transactions (account_id, booking_date, amount, category_id, counterparty, purpose, property_id, created_by, updated_by) VALUES
        (a_gbr, m + 14, -182.40, c_grundsteuer, 'Stadtkasse Musterstadt', 'Grundsteuer Haus Seeufer', p_seeufer, u_anna, u_anna),
        (a_gbr, m + 14, -236.10, c_grundsteuer, 'Gemeindekasse Seehausen', 'Grundsteuer Dünenblick', p_duene, u_anna, u_anna),
        (a_giro, m + 14, -311.75, c_grundsteuer, 'Stadtkasse Beispielstadt', 'Grundsteuer Lindenstraße', p_linden, u_anna, u_anna);
    END IF;
    IF extract(month FROM m) = 1 THEN
      INSERT INTO core.transactions (account_id, booking_date, amount, category_id, counterparty, purpose, property_id, created_by, updated_by) VALUES
        (a_gbr, m + 10, -428.00, c_vers, 'Musterversicherung AG', 'Gebäudeversicherung Haus Seeufer', p_seeufer, u_anna, u_anna),
        (a_gbr, m + 10, -515.00, c_vers, 'Musterversicherung AG', 'Gebäudeversicherung Dünenblick', p_duene, u_anna, u_anna),
        (a_giro, m + 10, -890.00, c_vers, 'Musterversicherung AG', 'Gebäudeversicherung Lindenstraße', p_linden, u_anna, u_anna);
    END IF;

    -- Anna: salary, rents from Lindenstraße, house money for her own flat, everyday spending.
    INSERT INTO core.transactions (account_id, booking_date, amount, category_id, counterparty, purpose, created_by, updated_by)
      VALUES (a_giro, (m + interval '1 month - 3 days')::date, 3240.00, c_gehalt, 'Muster AG', 'Gehalt ' || to_char(m, 'MM/YYYY'), u_anna, u_anna);
    INSERT INTO core.transactions (account_id, booking_date, amount, category_id, counterparty, purpose, unit_id, created_by, updated_by) VALUES
      (a_giro, m + 2, 650.00, c_miete, 'Lena Sommerfeld', 'Miete EG links', eg_links, u_anna, u_anna),
      (a_giro, m + 3, 720.00, c_miete, 'Paul Seemann', 'Miete EG rechts', eg_rechts, u_anna, u_anna),
      (a_giro, m + 1, 780.00, c_miete, 'Familie Wellenbrink', 'Miete 1. OG', og1, u_anna, u_anna),
      (a_giro, m + 4, 45.00, c_miete, 'Felix Kieselbach', 'Miete Garage', garage, u_anna, u_anna);
    -- The attic flat stands empty from March to May 2026 between two tenants.
    IF m < DATE '2026-03-01' OR m > DATE '2026-05-01' THEN
      INSERT INTO core.transactions (account_id, booking_date, amount, category_id, counterparty, purpose, unit_id, created_by, updated_by)
        VALUES (a_giro, m + 4, 540.00, c_miete, CASE WHEN m < DATE '2026-03-01' THEN 'Ida Brandung' ELSE 'Theo Muschelmann' END,
                'Miete DG', dg, u_anna, u_anna);
    END IF;
    INSERT INTO core.transactions (account_id, booking_date, amount, category_id, counterparty, purpose, property_id, created_by, updated_by)
      VALUES (a_giro, m + 1, -320.00, c_wohnen, 'Hausverwaltung Parkallee', 'Hausgeld', p_park, u_anna, u_anna),
             (a_giro, m + 6, -210.00, c_neben, 'Stadtwerke Beispielstadt', 'Allgemeinstrom und Wasser Lindenstraße', p_linden, u_anna, u_anna);
    FOR i IN 0..3 LOOP
      INSERT INTO core.transactions (account_id, booking_date, amount, category_id, counterparty, purpose, created_by, updated_by)
        VALUES (a_giro, m + 3 + i * 7, -round((45 + random() * 70)::numeric, 2), c_lebensmittel, 'Supermarkt am Markt', 'Einkauf', u_anna, u_anna);
    END LOOP;
    INSERT INTO core.transactions (account_id, booking_date, amount, category_id, counterparty, purpose, created_by, updated_by) VALUES
      (a_giro, m + 12, -round((55 + random() * 30)::numeric, 2), c_kraftstoff, 'Tankstelle Nord', 'Tanken', u_anna, u_anna),
      (a_giro, m + 1, -49.00, c_mobil, 'Verkehrsverbund', 'Monatskarte', u_anna, u_anna),
      (a_kasse, m + 8, -round((10 + random() * 25)::numeric, 2), c_sonst, 'Wochenmarkt', 'Bar bezahlt', u_anna, u_anna);
    -- A standing order to the savings account (a transfer has no category).
    tid := gen_random_uuid();
    INSERT INTO core.transactions (account_id, booking_date, amount, counterparty, purpose, transfer_id, created_by, updated_by) VALUES
      (a_giro, m + 5, -300.00, 'Tagesgeld', 'Sparrate', tid, u_anna, u_anna),
      (a_tages, m + 5, 300.00, 'Girokonto', 'Sparrate', tid, u_anna, u_anna);
    -- Cash for the market.
    tid := gen_random_uuid();
    INSERT INTO core.transactions (account_id, booking_date, amount, counterparty, purpose, transfer_id, created_by, updated_by) VALUES
      (a_giro, m + 7, -50.00, 'Barkasse', 'Bargeld', tid, u_anna, u_anna),
      (a_kasse, m + 7, 50.00, 'Girokonto', 'Bargeld', tid, u_anna, u_anna);
    IF extract(month FROM m) IN (3, 6, 9, 12) THEN
      INSERT INTO core.transactions (account_id, booking_date, amount, category_id, counterparty, purpose, created_by, updated_by)
        VALUES (a_tages, (m + interval '1 month - 1 day')::date, round((12 + random() * 10)::numeric, 2), c_kapital, 'Beispielbank', 'Zinsen', u_anna, u_anna);
    END IF;
    m := (m + interval '1 month')::date;
  END LOOP;

  -- Repairs as transactions and as property costs.
  INSERT INTO core.transactions (account_id, booking_date, amount, category_id, counterparty, purpose, unit_id, created_by, updated_by) VALUES
    (a_gbr, '2025-04-11', -840.00, c_instand, 'Malerbetrieb Farbenfroh', 'Anstrich Wohnzimmer', whg2, u_anna, u_anna),
    (a_gbr, '2026-03-20', -1290.00, c_instand, 'Sanitär Tropfnix', 'Neue Duschkabine', seeufer, u_anna, u_anna),
    (a_giro, '2025-09-02', -2650.00, c_instand, 'Dachdecker Ziegelmann', 'Dachrinne erneuert', NULL, u_anna, u_anna);
  UPDATE core.transactions SET property_id = p_linden WHERE purpose = 'Dachrinne erneuert';
  INSERT INTO core.property_costs (property_id, unit_id, cost_date, amount, category_id, counterparty, note) VALUES
    (p_linden, NULL, '2025-10-15', 185.00, c_instand, 'Heizung Warmhaus', 'Jährliche Heizungswartung'),
    (p_linden, NULL, '2026-10-14', 192.00, c_instand, 'Heizung Warmhaus', 'Jährliche Heizungswartung'),
    (p_linden, dg, '2026-05-20', 1450.00, c_instand, 'Malerbetrieb Farbenfroh', 'Renovierung vor Neuvermietung'),
    (p_seeufer, NULL, '2026-04-02', 310.00, c_instand, 'Gartenbau Grünfink', 'Hecke und Rasen'),
    (p_duene, whg1, '2025-06-01', 129.00, c_sonst, 'Möbelhaus Beispiel', 'Neue Bettwäsche');

  RAISE NOTICE 'Development data created';
END
$seed$;
