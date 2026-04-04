#!/usr/bin/env python3
"""
seed.py — Script de poblamiento TOTAL para inmobiliaria_db.
Elimina todo y reinserta datos limpios.
Respeta emails con '@' para validación de frontend.
"""

import os
import sys
from datetime import datetime, timezone, date, timedelta
import random

import bcrypt
from pymongo import MongoClient, ASCENDING
from pymongo.errors import PyMongoError

# --- Configuración ---
MONGO_URI: str = os.getenv(
    "MONGO_URI",
    "mongodb://admin:admin@localhost:27017/inmobiliaria_db?authSource=admin",
)
DB_NAME: str = os.getenv("MONGO_DB_NAME", "inmobiliaria_db")

BOOTSTRAP_ADMIN_EMAIL: str = os.getenv("BOOTSTRAP_ADMIN_EMAIL", "admin@admin")
BOOTSTRAP_ADMIN_PASSWORD: str = os.getenv("BOOTSTRAP_ADMIN_PASSWORD", "admin")
TESTING_PASSWORD: str = os.getenv("TESTING_PASSWORD", "password")

BCRYPT_ROUNDS: int = int(os.getenv("BCRYPT_ROUNDS", "12"))

# Poner en True para ejecutar limpio sin preguntar (Ideal para CI/CD y Dev)
SEED_FORCE_CLEAR: bool = True 

SYSTEM_ACTOR: str = "system"

# Colecciones a destruir y recrear
COLLECTIONS_TO_NUKE: list[str] = [
    "permissions_catalog", "roles", "users", "persons", "audit_logs",
    "properties", "calendar_events", "visit_requests",
    "employment_cycles", "password_reset_tokens", "refresh_tokens", "email_logs"
]

# --- Helpers ---
def now_utc() -> datetime:
    return datetime.now(timezone.utc)

def bcrypt_hash(password: str) -> str:
    return bcrypt.hashpw(
        password.encode("utf-8"),
        bcrypt.gensalt(rounds=BCRYPT_ROUNDS),
    ).decode("utf-8")

def date_to_datetime(d: date) -> datetime:
    return datetime(d.year, d.month, d.day, tzinfo=timezone.utc)

# --- Factories ---

def make_user(*, _id: str, first: str, last: str, email: str, password_hash: str,
              user_type: str, role_ids: list[str], current_time: datetime) -> dict:
    return {
        "_id": _id,
        "firstName": first,
        "lastName": last,
        "fullName": f"{first} {last}",
        "email": email,
        "emailNormalized": email.strip().lower(),
        "passwordHash": password_hash,
        "userType": user_type,
        "status": "ACTIVE",
        "temporaryPassword": False,
        "temporaryPasswordExpiresAt": None,
        "mustChangePassword": False,
        "passwordChangedAt": None,
        "failedLoginAttempts": 0,
        "lockedUntil": None,
        "lastLoginAt": None,
        "primaryRoleIds": role_ids,
        "activeEmploymentCycleId": None,
        "metadata": {"testing": True},
        "updatedAt": current_time,
        "createdBy": SYSTEM_ACTOR,
        "createdAt": current_time,
    }

def make_person(*, auth_user_id: str, first: str, last: str, email: str, phone: str,
                birth_date: date, person_type: str, role_ids: list[str],
                current_time: datetime, **kwargs) -> dict:
    birth_datetime = date_to_datetime(birth_date)
    
    type_alias_map = {
        "ADMIN": "admin",
        "EMPLOYEE": "employee",
        "OWNER": "owner",
        "INTERESTED_CLIENT": "interested_client"
    }
    
    person_doc = {
        "_id": f"person_{auth_user_id}",
        "authUserId": auth_user_id,
        "_class": type_alias_map.get(person_type, "person"),
        "firstName": first,
        "lastName": last,
        "fullName": f"{first} {last}",
        "birthDate": birth_datetime,
        "phone": phone,
        "email": email,
        "personType": person_type,
        "roleIds": role_ids,
        "customRole": False,
        "createdAt": current_time,
        "updatedAt": current_time,
        "createdBy": SYSTEM_ACTOR,
    }
    
    if person_type == "EMPLOYEE":
        person_doc["department"] = kwargs.get("department", "General")
        person_doc["position"] = kwargs.get("position", "Agent")
        person_doc["hireDate"] = kwargs.get("hireDate", current_time)
        person_doc["assignedClientIds"] = kwargs.get("assignedClientIds", [])
    elif person_type == "OWNER":
        person_doc["taxId"] = kwargs.get("taxId", f"NIT-{random.randint(100000, 999999)}")
        person_doc["address"] = kwargs.get("address", "Address not specified")
        person_doc["propertyIds"] = kwargs.get("propertyIds", [])
    elif person_type == "INTERESTED_CLIENT":
        person_doc["preferredContactMethod"] = kwargs.get("preferredContactMethod", "EMAIL")
        person_doc["budget"] = kwargs.get("budget", "0")
        person_doc["preferredZone"] = kwargs.get("preferredZone", "Any")
        person_doc["preferredPropertyType"] = kwargs.get("preferredPropertyType", "CASA")
        person_doc["preferredRooms"] = kwargs.get("preferredRooms", 3)
    
    return person_doc

def make_property(*, _id: str, title: str, address: str, price: float, m2: float, 
                  rooms: int, type_prop: str, operation_type: str, status: str, 
                  owner_id: str, agent_id: str, current_time: datetime) -> dict:
    return {
        "_id": _id,
        "title": title,
        "address": address,
        "price": price,
        "type": type_prop, 
        "operationType": operation_type, 
        "m2": m2,
        "rooms": rooms,
        "status": status, 
        "assignedAgentId": agent_id,
        "ownerId": owner_id,
        "imageUrls": [],
        "assignmentHistory": [{
            "agentId": agent_id,
            "assignedAt": current_time,
            "assignedBy": SYSTEM_ACTOR
        }],
        "priceHistory": [],
        "accessPolicy": ["ROLE_AGENT", "ROLE_ADMIN"],
        "documents": [],
        "images": [],
        "deleted": False,
        "createdAt": current_time,
        "updatedAt": current_time,
        "createdBy": agent_id
    }

def make_calendar_event(*, _id, prop_id, prop_name, agent_id, agent_name, start_time, end_time, current_time, status="SCHEDULED"):
    return {
        "_id": _id,
        "propertyId": prop_id,
        "propertyName": prop_name,
        "agentId": agent_id,
        "agentName": agent_name,
        "startTime": start_time,
        "endTime": end_time,
        "type": "VISIT", 
        "status": status, 
        "createdAt": current_time
    }

def make_visit_request(*, _id, prop_id, prop_name, agent_id, agent_name, client_id, client_name, client_email, pref_time, current_time):
    return {
        "_id": _id,
        "propertyId": prop_id,
        "propertyName": prop_name,
        "agentId": agent_id,
        "agentName": agent_name,
        "clientId": client_id,
        "clientName": client_name,
        "clientEmail": client_email,
        "preferredDateTime": pref_time,
        "status": "PENDING",
        "createdAt": current_time,
        "notificationSent": False
    }

# --- DB Operations ---

def confirm_and_nuke(db) -> None:
    if not SEED_FORCE_CLEAR:
        print("\n⚠️  WARNING ⚠️")
        print("This will DROP (delete completely) the following collections:")
        for col in COLLECTIONS_TO_NUKE:
            print(f"  - {col}")
        print(f"\n  Database: {DB_NAME}\n")
        answer = input("Continue? Type 'yes' to confirm: ").strip().lower()
        if answer != "yes":
            print("Operation cancelled.")
            sys.exit(0)
    else:
        print("SEED_FORCE_CLEAR=true — NUKING ALL COLLECTIONS...")

    # ESTRATEGIA NUCLEAR: Drop collections
    for col_name in COLLECTIONS_TO_NUKE:
        try:
            db[col_name].drop()
            print(f"  ✓ Dropped collection: {col_name}")
        except Exception as e:
            print(f"  ! Could not drop {col_name} (might not exist): {e}")

def ensure_indexes(db) -> None:
    print("\nCreating fresh indexes on clean collections...")
    
    # Permissions
    db.permissions_catalog.create_index(
        [("resource", ASCENDING), ("action", ASCENDING), ("scope", ASCENDING)],
        unique=True, name="uk_perm_res_act_sco"
    )
    
    # Roles
    db.roles.create_index([("code", ASCENDING)], unique=True, name="uk_roles_code")
    db.roles.create_index([("name", ASCENDING)], unique=True, name="uk_roles_name")
    
    # Users
    db.users.create_index([("emailNormalized", ASCENDING)], unique=True, name="uk_users_email")
    db.users.create_index([("status", ASCENDING)], name="idx_users_status")
    
    # Persons
    db.persons.create_index([("authUserId", ASCENDING)], unique=True, name="uk_persons_auth")
    db.persons.create_index([("personType", ASCENDING)], name="idx_persons_type")
    
    # Properties
    db.properties.create_index([("assignedAgentId", ASCENDING)], name="idx_prop_agent")
    db.properties.create_index([("ownerId", ASCENDING)], name="idx_prop_owner")
    db.properties.create_index([("status", ASCENDING)], name="idx_prop_status")
    
    # Calendar & Visits
    db.calendar_events.create_index([("propertyId", ASCENDING), ("startTime", ASCENDING)], name="idx_cal_prop_time")
    db.calendar_events.create_index([("agentId", ASCENDING), ("startTime", ASCENDING)], name="idx_cal_agent_time")
    db.visit_requests.create_index([("agentId", ASCENDING), ("status", ASCENDING)], name="idx_visit_agent_status")
    
    print("Indexes created successfully.\n")

def upsert_many(collection_name, db, documents: list[dict], current_time: datetime) -> int:
    count = 0
    col = db[collection_name]
    for doc in documents:
        _id = doc["_id"]
        payload = {k: v for k, v in doc.items() if k not in ["createdAt", "_id"]}
        payload["updatedAt"] = current_time
        created_at = doc.get("createdAt", current_time)
        
        col.update_one(
            {"_id": _id},
            {"$set": payload, "$setOnInsert": {"createdAt": created_at}},
            upsert=True,
        )
        count += 1
    print(f"  ✓ Processed {count} documents in {collection_name}")
    return count

# --- Builders ---

def build_permissions(current_time: datetime) -> list[dict]:
    return [
        {"_id": "perm_auth_login", "resource": "auth", "action": "login", "scope": "ANY", "description": "Login", "active": True, "createdBy": SYSTEM_ACTOR, "createdAt": current_time},
        {"_id": "perm_users_create", "resource": "users", "action": "create", "scope": "ANY", "description": "Create users", "active": True, "createdBy": SYSTEM_ACTOR, "createdAt": current_time},
        {"_id": "perm_props_create", "resource": "properties", "action": "create", "scope": "ANY", "description": "Create props", "active": True, "createdBy": SYSTEM_ACTOR, "createdAt": current_time},
    ]

def build_roles(current_time: datetime, perms) -> list[dict]:
    return [
        {
            "_id": "rol_admin", "code": "ADMIN", "name": "Administrator", "type": "SYSTEM", "active": True,
            "permissions": perms, "version": 1, "createdBy": SYSTEM_ACTOR, "createdAt": current_time
        },
        {
            "_id": "rol_agent", "code": "AGENT", "name": "Agent", "type": "SYSTEM", "active": True,
            "permissions": perms, "version": 1, "createdBy": SYSTEM_ACTOR, "createdAt": current_time
        },
        {
            "_id": "rol_owner", "code": "OWNER", "name": "Owner", "type": "SYSTEM", "active": True,
            "permissions": [], "version": 1, "createdBy": SYSTEM_ACTOR, "createdAt": current_time
        },
        {
            "_id": "rol_interested_client", "code": "INTERESTED_CLIENT", "name": "Client", "type": "SYSTEM", "active": True,
            "permissions": [], "version": 1, "createdBy": SYSTEM_ACTOR, "createdAt": current_time
        },
    ]

def build_full_data(current_time: datetime, role_id_map: dict):
    ph = bcrypt_hash(TESTING_PASSWORD)
    birth_date = date(1990, 1, 1)
    
    users = []
    persons = []
    properties = []
    events = []
    requests = []
    
    # --- 1. USERS & PERSONS (Con emails válidos @) ---
    
    # Admin
    u_admin = "usr_test_admin"
    users.append(make_user(_id=u_admin, first="Admin", last="System", email="admin@admin", password_hash=ph, user_type="ADMIN", role_ids=[role_id_map["ADMIN"]], current_time=current_time))
    persons.append(make_person(auth_user_id=u_admin, first="Admin", last="System", email="admin@admin", phone="111", birth_date=birth_date, person_type="ADMIN", role_ids=[role_id_map["ADMIN"]], current_time=current_time))

    # Agents
    u_agent1 = "usr_test_agent_1"
    users.append(make_user(_id=u_agent1, first="John", last="Doe", email="agent1@user", password_hash=ph, user_type="EMPLOYEE", role_ids=[role_id_map["AGENT"]], current_time=current_time))
    persons.append(make_person(auth_user_id=u_agent1, first="John", last="Doe", email="agent1@user", phone="222", birth_date=birth_date, person_type="EMPLOYEE", role_ids=[role_id_map["AGENT"]], current_time=current_time, department="Ventas"))

    u_agent2 = "usr_test_agent_2"
    users.append(make_user(_id=u_agent2, first="Jane", last="Smith", email="agent2@user", password_hash=ph, user_type="EMPLOYEE", role_ids=[role_id_map["AGENT"]], current_time=current_time))
    persons.append(make_person(auth_user_id=u_agent2, first="Jane", last="Smith", email="agent2@user", phone="333", birth_date=birth_date, person_type="EMPLOYEE", role_ids=[role_id_map["AGENT"]], current_time=current_time, department="Alquileres"))

    # Owners
    u_owner1 = "usr_test_owner_1"
    users.append(make_user(_id=u_owner1, first="Mike", last="Owner", email="owner1@user", password_hash=ph, user_type="OWNER", role_ids=[role_id_map["OWNER"]], current_time=current_time))
    persons.append(make_person(auth_user_id=u_owner1, first="Mike", last="Owner", email="owner1@user", phone="444", birth_date=birth_date, person_type="OWNER", role_ids=[role_id_map["OWNER"]], current_time=current_time, taxId="NIT-111"))

    u_owner2 = "usr_test_owner_2"
    users.append(make_user(_id=u_owner2, first="Sarah", last="Connor", email="owner2@user", password_hash=ph, user_type="OWNER", role_ids=[role_id_map["OWNER"]], current_time=current_time))
    persons.append(make_person(auth_user_id=u_owner2, first="Sarah", last="Connor", email="owner2@user", phone="445", birth_date=birth_date, person_type="OWNER", role_ids=[role_id_map["OWNER"]], current_time=current_time, taxId="NIT-222"))

    # Client
    u_client1 = "usr_test_client_1"
    users.append(make_user(_id=u_client1, first="Boo", last="Client", email="client1@user", password_hash=ph, user_type="INTERESTED_CLIENT", role_ids=[role_id_map["INTERESTED_CLIENT"]], current_time=current_time))
    persons.append(make_person(auth_user_id=u_client1, first="Boo", last="Client", email="client1@user", phone="555", birth_date=birth_date, person_type="INTERESTED_CLIENT", role_ids=[role_id_map["INTERESTED_CLIENT"]], current_time=current_time, budget="150000"))

    # --- 2. PROPERTIES ---
    
    # Propiedad 1: Venta, asignada a Agent 1, dueño Owner 1
    p1_id = "prop_venta_001"
    properties.append(make_property(
        _id=p1_id, title="Casa de Lujo Zona Sur", address="Av. Principal 123",
        price=250000.0, m2=300.0, rooms=4, type_prop="CASA", operation_type="VENTA",
        status="DISPONIBLE", owner_id=u_owner1, agent_id=u_agent1, current_time=current_time
    ))

    # Propiedad 2: Alquiler, asignada a Agent 1, dueño Owner 2
    p2_id = "prop_alq_002"
    properties.append(make_property(
        _id=p2_id, title="Apartamento Moderno Centro", address="Calle Secundaria 45",
        price=500.0, m2=80.0, rooms=2, type_prop="APARTAMENTO", operation_type="ALQUILER",
        status="DISPONIBLE", owner_id=u_owner2, agent_id=u_agent1, current_time=current_time
    ))

    # Propiedad 3: Anticretico, asignada a Agent 2, dueño Owner 1
    p3_id = "prop_anti_003"
    properties.append(make_property(
        _id=p3_id, title="Local Comercial Av. Principal", address="Av. Principal 500",
        price=30000.0, m2=100.0, rooms=1, type_prop="LOCAL", operation_type="ANTICRETICO",
        status="DISPONIBLE", owner_id=u_owner1, agent_id=u_agent2, current_time=current_time
    ))

    # --- 3. CALENDAR EVENTS ---
    
    tomorrow_10am = datetime.now(timezone.utc) + timedelta(days=1)
    tomorrow_10am = tomorrow_10am.replace(hour=10, minute=0, second=0, microsecond=0)
    
    events.append(make_calendar_event(
        _id="evt_001", prop_id=p1_id, prop_name="Casa de Lujo Zona Sur",
        agent_id=u_agent1, agent_name="John Doe",
        start_time=tomorrow_10am, end_time=tomorrow_10am + timedelta(hours=1),
        current_time=current_time
    ))

    # --- 4. VISIT REQUESTS ---
    
    requests.append(make_visit_request(
        _id="req_001", prop_id=p3_id, prop_name="Local Comercial Av. Principal",
        agent_id=u_agent2, agent_name="Jane Smith",
        client_id=u_client1, client_name="Boo Client", client_email="client1@user",
        pref_time=tomorrow_10am + timedelta(days=1),
        current_time=current_time
    ))

    # Update back-references in Persons
    for p in persons:
        if p["authUserId"] == u_owner1:
            p["propertyIds"] = [p1_id, p3_id]
        if p["authUserId"] == u_owner2:
            p["propertyIds"] = [p2_id]

    return users, persons, properties, events, requests

# --- Main ---

def main() -> None:
    current_time = now_utc()
    client = None

    try:
        print("Connecting to MongoDB...")
        client = MongoClient(MONGO_URI)
        client.admin.command("ping")
        print("Connection OK.\n")

        db = client[DB_NAME]

        # 1. Nuke (Drop Collections)
        confirm_and_nuke(db)

        # 2. Create Indexes (On fresh collections)
        ensure_indexes(db)

        # 3. Seed Data
        print("Seeding permissions & roles...")
        perms = build_permissions(current_time)
        upsert_many("permissions_catalog", db, perms, current_time)
        
        roles = build_roles(current_time, perms)
        upsert_many("roles", db, roles, current_time)
        role_id_map = {r["code"]: r["_id"] for r in roles}

        print("\nSeeding Users, Persons, Properties, Calendar...")
        users, persons, properties, events, requests = build_full_data(current_time, role_id_map)
        
        upsert_many("users", db, users, current_time)
        upsert_many("persons", db, persons, current_time)
        
        print(f"\n>>> Inserting {len(properties)} PROPERTIES...")
        upsert_many("properties", db, properties, current_time)
        
        print(f"\n>>> Inserting {len(events)} CALENDAR EVENTS...")
        upsert_many("calendar_events", db, events, current_time)
        
        print(f"\n>>> Inserting {len(requests)} VISIT REQUESTS...")
        upsert_many("visit_requests", db, requests, current_time)

        print("\n" + "="*50)
        print("SEED FINISHED SUCCESSFULLY")
        print(f"Properties created: {db.properties.count_documents({})}")
        print(f"Events created: {db.calendar_events.count_documents({})}")
        print(f"Requests created: {db.visit_requests.count_documents({})}")
        print(f"Login: {BOOTSTRAP_ADMIN_EMAIL} / {BOOTSTRAP_ADMIN_PASSWORD}")
        print("="*50 + "\n")

    except PyMongoError as exc:
        print(f"MongoDB Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except Exception as exc:
        print(f"Unexpected error: {exc}", file=sys.stderr)
        sys.exit(1)
    finally:
        if client is not None:
            client.close()

if __name__ == "__main__":
    main()