#!/usr/bin/env python3
"""
seed.py — Bootstrap script for inmobiliaria_db.
"""

import os
import sys
from datetime import datetime, timezone, date

import bcrypt
from pymongo import MongoClient, ASCENDING
from pymongo.errors import PyMongoError

MONGO_URI: str = os.getenv(
    "MONGO_URI",
    "mongodb://admin:admin@localhost:27017/inmobiliaria_db?authSource=admin",
)
DB_NAME: str = os.getenv("MONGO_DB_NAME", "inmobiliaria_db")

BOOTSTRAP_ADMIN_EMAIL: str = os.getenv("BOOTSTRAP_ADMIN_EMAIL", "admin@admin")
BOOTSTRAP_ADMIN_PASSWORD: str = os.getenv("BOOTSTRAP_ADMIN_PASSWORD", "admin")

TESTING_PASSWORD: str = os.getenv("TESTING_PASSWORD", "password")

BCRYPT_ROUNDS: int = int(os.getenv("BCRYPT_ROUNDS", "12"))

ROTATE_BOOTSTRAP_ADMIN_PASSWORD_ON_RESEED: bool = (
    os.getenv("ROTATE_BOOTSTRAP_ADMIN_PASSWORD_ON_RESEED", "false").lower() == "true"
)

SEED_FORCE_CLEAR: bool = os.getenv("SEED_FORCE_CLEAR", "false").lower() == "true"

SYSTEM_ACTOR: str = "system"

COLLECTIONS_TO_CLEAR: list[str] = [
    "permissions_catalog",
    "roles",
    "users",
    "persons",
    "audit_events",
    "employment_cycles",
    "password_reset_tokens",
    "refresh_tokens",
]

COLLECTIONS_WITH_INDEXES: list[str] = [
    "permissions_catalog",
    "roles",
    "users",
    "persons",
    "employment_cycles",
    "password_reset_tokens",
    "refresh_tokens",
    "audit_events",
]


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def bcrypt_hash(password: str) -> str:
    return bcrypt.hashpw(
        password.encode("utf-8"),
        bcrypt.gensalt(rounds=BCRYPT_ROUNDS),
    ).decode("utf-8")


def date_to_datetime(d: date) -> datetime:
    """Convert date to datetime with UTC timezone at midnight."""
    return datetime(d.year, d.month, d.day, tzinfo=timezone.utc)


def make_user(
    *,
    _id: str,
    first: str,
    last: str,
    email: str,
    password_hash: str,
    user_type: str,
    role_ids: list[str],
    current_time: datetime,
) -> dict:
    """Build a normalized user document ready for upsert."""
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
        "metadata": {"testing": True} if _id.startswith("usr_test") else {},
        "updatedAt": current_time,
        "createdBy": SYSTEM_ACTOR,
        "createdAt": current_time,
    }


def make_person(
    *,
    auth_user_id: str,
    first: str,
    last: str,
    email: str,
    phone: str,
    birth_date: date,
    person_type: str,
    role_ids: list[str],
    current_time: datetime,
    **kwargs
) -> dict:
    """Build a person document linked to the auth user."""
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
        person_doc["position"] = kwargs.get("position", "Employee")
        person_doc["hireDate"] = kwargs.get("hireDate", current_time)
    elif person_type == "OWNER":
        person_doc["taxId"] = kwargs.get("taxId", "NIT-123456")
    elif person_type == "INTERESTED_CLIENT":
        person_doc["preferredContactMethod"] = kwargs.get("preferredContactMethod", "EMAIL")
        person_doc["budget"] = kwargs.get("budget", "0")
        # Campos nuevos
        person_doc["preferredZone"] = kwargs.get("preferredZone", None)
        person_doc["preferredPropertyType"] = kwargs.get("preferredPropertyType", None)
        person_doc["preferredRooms"] = kwargs.get("preferredRooms", None)
    
    return person_doc


def confirm_and_clear(db) -> None:
    if not SEED_FORCE_CLEAR:
        print("\n⚠️  WARNING ⚠️")
        print("This will DELETE ALL documents from the following collections:")
        for col in COLLECTIONS_TO_CLEAR:
            print(f"  - {col}")
        print(f"\n  Database: {DB_NAME}\n")
        answer = input("Continue? Type 'yes' to confirm: ").strip().lower()
        if answer != "yes":
            print("Operation cancelled.")
            sys.exit(0)
    else:
        print("SEED_FORCE_CLEAR=true — clearing without confirmation...")

    print("\nClearing collections...")
    for col_name in COLLECTIONS_TO_CLEAR:
        result = db[col_name].delete_many({})
        print(f"  ✓ {col_name}: {result.deleted_count} document(s) deleted")
    print("Clear complete.\n")


def drop_collection_indexes(db) -> None:
    print("Removing existing indexes...")
    for col_name in COLLECTIONS_WITH_INDEXES:
        try:
            col = db[col_name]
            non_default = [idx["name"] for idx in col.list_indexes() if idx["name"] != "_id_"]
            for idx_name in non_default:
                col.drop_index(idx_name)
                print(f"  ✓ {col_name}.{idx_name} dropped")
        except Exception as e:
            pass
    print("Indexes removed.\n")


def ensure_indexes(db) -> None:
    db.permissions_catalog.create_index(
        [("resource", ASCENDING), ("action", ASCENDING), ("scope", ASCENDING)],
        unique=True,
        name="uk_permission_catalog_resource_action_scope",
    )

    db.roles.create_index([("code", ASCENDING)], unique=True, name="uk_roles_code")
    db.roles.create_index([("name", ASCENDING)], unique=True, name="uk_roles_name")

    db.users.create_index(
        [("emailNormalized", ASCENDING)], unique=True, name="uk_users_email_normalized"
    )
    db.users.create_index([("status", ASCENDING)], name="idx_users_status")
    db.users.create_index(
        [("activeEmploymentCycleId", ASCENDING)], name="idx_users_active_employment_cycle_id"
    )
    db.users.create_index(
        [("primaryRoleIds", ASCENDING)], name="idx_users_primary_role_ids"
    )

    db.persons.create_index(
        [("authUserId", ASCENDING)], unique=True, name="uk_persons_auth_user_id"
    )
    db.persons.create_index([("personType", ASCENDING)], name="idx_persons_person_type")

    db.employment_cycles.create_index(
        [("userId", ASCENDING), ("employmentStatus", ASCENDING)],
        name="idx_employment_cycles_user_status",
    )

    db.password_reset_tokens.create_index(
        [("expiresAt", ASCENDING)],
        expireAfterSeconds=0,
        name="ttl_password_reset_tokens_expires_at",
    )

    db.refresh_tokens.create_index(
        [("expiresAt", ASCENDING)],
        expireAfterSeconds=0,
        name="ttl_refresh_tokens_expires_at",
    )

    db.audit_events.create_index([("eventType", ASCENDING)], name="idx_audit_events_event_type")
    db.audit_events.create_index([("occurredAt", ASCENDING)], name="idx_audit_events_occurred_at")


def upsert_many_by_id(collection, documents: list[dict], current_time: datetime) -> int:
    """Upsert documents by _id."""
    count = 0
    for doc in documents:
        _id = doc["_id"]
        payload = {k: v for k, v in doc.items() if k not in ["createdAt", "_id"]}
        payload["updatedAt"] = current_time
        created_at = doc.get("createdAt", current_time)

        collection.update_one(
            {"_id": _id},
            {"$set": payload, "$setOnInsert": {"createdAt": created_at}},
            upsert=True,
        )
        count += 1

    return count


def build_permissions(current_time: datetime) -> list[dict]:
    definitions = [
        ("perm_auth_login_any",                 "auth",                "login",           "ANY",      "Login"),
        ("perm_auth_refresh_any",               "auth",                "refresh",         "ANY",      "Refresh token"),
        ("perm_auth_logout_any",                "auth",                "logout",          "ANY",      "Logout"),
        ("perm_auth_change_password_own",       "auth",                "change_password", "OWN",      "Change own password"),
        ("perm_auth_forgot_password_any",       "auth",                "forgot_password", "ANY",      "Request password reset"),
        ("perm_auth_reset_password_any",        "auth",                "reset_password",  "ANY",      "Reset password with token"),

        ("perm_users_create_any",               "users",               "create",          "ANY",      "Create users"),
        ("perm_users_read_any",                 "users",               "read",            "ANY",      "Read any user"),
        ("perm_users_read_own",                 "users",               "read",            "OWN",      "Read own profile"),
        ("perm_users_update_any",               "users",               "update",          "ANY",      "Update any user"),
        ("perm_users_update_own",               "users",               "update",          "OWN",      "Update own profile"),
        ("perm_users_assign_role_any",          "users",               "assign_role",     "ANY",      "Assign roles to users"),
        ("perm_users_change_status_any",        "users",               "change_status",   "ANY",      "Change user status"),

        ("perm_roles_create_any",               "roles",               "create",          "ANY",      "Create roles"),
        ("perm_roles_read_any",                 "roles",               "read",            "ANY",      "Read roles"),
        ("perm_roles_update_any",               "roles",               "update",          "ANY",      "Update roles"),
        ("perm_roles_delete_any",               "roles",               "delete",          "ANY",      "Delete roles"),

        ("perm_admin_dashboard_read_any",       "admin_dashboard",     "read",            "ANY",      "Access admin dashboard"),
        ("perm_audit_read_any",                 "audit",               "read",            "ANY",      "Read system audit"),

        ("perm_properties_create_any",          "properties",          "create",          "ANY",      "Create properties"),
        ("perm_properties_read_any",            "properties",          "read",            "ANY",      "Read any property"),
        ("perm_properties_read_assigned",       "properties",          "read",            "ASSIGNED", "Read assigned properties"),
        ("perm_properties_read_own",            "properties",          "read",            "OWN",      "Read own properties"),
        ("perm_properties_read_matched",        "properties",          "read",            "MATCHED",  "Read matched properties"),
        ("perm_properties_update_any",          "properties",          "update",          "ANY",      "Update any property"),
        ("perm_properties_update_assigned",     "properties",          "update",          "ASSIGNED", "Update assigned properties"),

        ("perm_property_price_set_initial_any", "property_price",      "set_initial",     "ANY",      "Set initial price"),
        ("perm_property_price_change_any",      "property_price",      "change",          "ANY",      "Change price"),
        ("perm_property_assignment_assign_any", "property_assignment", "assign",          "ANY",      "Assign property to agent"),
        ("perm_property_assignment_reassign_any","property_assignment","reassign",        "ANY",      "Reassign property"),

        ("perm_contracts_read_any",             "contracts",           "read",            "ANY",      "Read any contract"),
        ("perm_contracts_read_own",             "contracts",           "read",            "OWN",      "Read own contracts"),
        ("perm_contracts_read_assigned",        "contracts",           "read",            "ASSIGNED", "Read assigned contracts"),

        ("perm_client_preferences_read_own",    "client_preferences",  "read",            "OWN",      "Read own preferences"),
        ("perm_client_preferences_update_own",  "client_preferences",  "update",          "OWN",      "Update own preferences"),
        ("perm_matches_read_own",               "matches",             "read",            "OWN",      "Read own matches"),

        ("perm_calendar_events_read_any",        "calendar_events",    "read",            "ANY",      "Read any calendar events"),
        ("perm_calendar_events_read_own",        "calendar_events",    "read",            "OWN",      "Read own events"),
        ("perm_calendar_events_read_assigned",   "calendar_events",    "read",            "ASSIGNED", "Read assigned events"),
        ("perm_calendar_events_create_any",      "calendar_events",    "create",          "ANY",      "Create calendar events"),
        ("perm_calendar_events_update_any",      "calendar_events",    "update",          "ANY",      "Update any calendar events"),
        ("perm_calendar_events_update_assigned", "calendar_events",    "update",          "ASSIGNED", "Update assigned events"),

        ("perm_resources_read_any",             "resources",           "read",            "ANY",      "Read resources"),
        ("perm_resources_read_assigned",        "resources",           "read",            "ASSIGNED", "Read assigned resources"),
        ("perm_resources_assign_any",           "resources",           "assign",          "ANY",      "Assign resources"),
        ("perm_resources_reassign_any",         "resources",           "reassign",        "ANY",      "Reassign resources"),
        ("perm_budgets_read_any",               "budgets",             "read",            "ANY",      "Read budgets"),
        ("perm_budgets_read_assigned",          "budgets",             "read",            "ASSIGNED", "Read assigned budgets"),
        ("perm_budgets_assign_any",             "budgets",             "assign",          "ANY",      "Assign budget"),
        ("perm_budgets_approve_any",            "budgets",             "approve",         "ANY",      "Approve budget"),
    ]

    return [
        {
            "_id": _id,
            "resource": resource,
            "action": action,
            "scope": scope,
            "description": description,
            "active": True,
            "createdBy": SYSTEM_ACTOR,
            "createdAt": current_time,
        }
        for _id, resource, action, scope, description in definitions
    ]


def build_roles(current_time: datetime) -> list[dict]:
    role_admin_permissions = [
        {"resource": "auth", "action": "change_password", "scope": "OWN"},
        {"resource": "users", "action": "create", "scope": "ANY"},
        {"resource": "users", "action": "read", "scope": "ANY"},
        {"resource": "users", "action": "update", "scope": "ANY"},
        {"resource": "users", "action": "assign_role", "scope": "ANY"},
        {"resource": "users", "action": "change_status", "scope": "ANY"},
        {"resource": "roles", "action": "create", "scope": "ANY"},
        {"resource": "roles", "action": "read", "scope": "ANY"},
        {"resource": "roles", "action": "update", "scope": "ANY"},
        {"resource": "roles", "action": "delete", "scope": "ANY"},
        {"resource": "admin_dashboard", "action": "read", "scope": "ANY"},
        {"resource": "audit", "action": "read", "scope": "ANY"},
        {"resource": "properties", "action": "create", "scope": "ANY"},
        {"resource": "properties", "action": "read", "scope": "ANY"},
        {"resource": "properties", "action": "update", "scope": "ANY"},
        {"resource": "property_price", "action": "set_initial", "scope": "ANY"},
        {"resource": "property_price", "action": "change", "scope": "ANY"},
        {"resource": "property_assignment", "action": "assign", "scope": "ANY"},
        {"resource": "property_assignment", "action": "reassign", "scope": "ANY"},
        {"resource": "contracts", "action": "read", "scope": "ANY"},
        {"resource": "calendar_events", "action": "read", "scope": "ANY"},
        {"resource": "calendar_events", "action": "create", "scope": "ANY"},
        {"resource": "calendar_events", "action": "update", "scope": "ANY"},
        {"resource": "resources", "action": "read", "scope": "ANY"},
        {"resource": "resources", "action": "assign", "scope": "ANY"},
        {"resource": "resources", "action": "reassign", "scope": "ANY"},
        {"resource": "budgets", "action": "read", "scope": "ANY"},
        {"resource": "budgets", "action": "assign", "scope": "ANY"},
        {"resource": "budgets", "action": "approve", "scope": "ANY"},
    ]

    role_agent_permissions = [
        {"resource": "auth", "action": "change_password", "scope": "OWN"},
        {"resource": "users", "action": "read", "scope": "OWN"},
        {"resource": "users", "action": "update", "scope": "OWN"},
        {"resource": "properties", "action": "create", "scope": "ANY"},
        {"resource": "properties", "action": "read", "scope": "ASSIGNED"},
        {"resource": "properties", "action": "update", "scope": "ASSIGNED"},
        {"resource": "property_price", "action": "set_initial", "scope": "ANY"},
        {"resource": "contracts", "action": "read", "scope": "ASSIGNED"},
        {"resource": "calendar_events", "action": "read", "scope": "ASSIGNED"},
        {"resource": "calendar_events", "action": "update", "scope": "ASSIGNED"},
        {"resource": "resources", "action": "read", "scope": "ASSIGNED"},
        {"resource": "budgets", "action": "read", "scope": "ASSIGNED"},
    ]

    role_owner_permissions = [
        {"resource": "auth", "action": "change_password", "scope": "OWN"},
        {"resource": "users", "action": "read", "scope": "OWN"},
        {"resource": "users", "action": "update", "scope": "OWN"},
        {"resource": "properties", "action": "read", "scope": "OWN"},
        {"resource": "contracts", "action": "read", "scope": "OWN"},
    ]

    role_interested_permissions = [
        {"resource": "auth", "action": "change_password", "scope": "OWN"},
        {"resource": "users", "action": "read", "scope": "OWN"},
        {"resource": "users", "action": "update", "scope": "OWN"},
        {"resource": "client_preferences", "action": "read", "scope": "OWN"},
        {"resource": "client_preferences", "action": "update", "scope": "OWN"},
        {"resource": "matches", "action": "read", "scope": "OWN"},
        {"resource": "properties", "action": "read", "scope": "MATCHED"},
    ]

    role_supervisor_permissions = [
        {"resource": "auth", "action": "change_password", "scope": "OWN"},
        {"resource": "users", "action": "read", "scope": "ANY"},
        {"resource": "users", "action": "update", "scope": "ANY"},
        {"resource": "users", "action": "change_status", "scope": "ANY"},
        {"resource": "admin_dashboard", "action": "read", "scope": "ANY"},
        {"resource": "properties", "action": "create", "scope": "ANY"},
        {"resource": "properties", "action": "read", "scope": "ANY"},
        {"resource": "properties", "action": "update", "scope": "ANY"},
        {"resource": "property_price", "action": "set_initial", "scope": "ANY"},
        {"resource": "property_price", "action": "change", "scope": "ANY"},
        {"resource": "property_assignment", "action": "assign", "scope": "ANY"},
        {"resource": "property_assignment", "action": "reassign", "scope": "ANY"},
        {"resource": "contracts", "action": "read", "scope": "ANY"},
        {"resource": "calendar_events", "action": "read", "scope": "ANY"},
        {"resource": "calendar_events", "action": "create", "scope": "ANY"},
        {"resource": "calendar_events", "action": "update", "scope": "ANY"},
        {"resource": "resources", "action": "read", "scope": "ANY"},
        {"resource": "budgets", "action": "read", "scope": "ANY"},
        {"resource": "budgets", "action": "approve", "scope": "ANY"},
    ]

    role_viewer_permissions = [
        {"resource": "auth", "action": "change_password", "scope": "OWN"},
        {"resource": "users", "action": "read", "scope": "ANY"},
        {"resource": "roles", "action": "read", "scope": "ANY"},
        {"resource": "properties", "action": "read", "scope": "ANY"},
        {"resource": "contracts", "action": "read", "scope": "ANY"},
        {"resource": "calendar_events", "action": "read", "scope": "ANY"},
        {"resource": "resources", "action": "read", "scope": "ANY"},
        {"resource": "budgets", "action": "read", "scope": "ANY"},
        {"resource": "audit", "action": "read", "scope": "ANY"},
    ]

    return [
        {
            "_id": "rol_admin",
            "code": "ADMIN",
            "name": "Administrator",
            "description": "Full system access",
            "type": "SYSTEM",
            "active": True,
            "permissions": role_admin_permissions,
            "version": 1,
            "createdBy": SYSTEM_ACTOR,
            "createdAt": current_time,
        },
        {
            "_id": "rol_agent",
            "code": "AGENT",
            "name": "Agent",
            "description": "Operational role for real estate agents",
            "type": "SYSTEM",
            "active": True,
            "permissions": role_agent_permissions,
            "version": 1,
            "createdBy": SYSTEM_ACTOR,
            "createdAt": current_time,
        },
        {
            "_id": "rol_owner",
            "code": "OWNER",
            "name": "Property Owner",
            "description": "Property owner client",
            "type": "SYSTEM",
            "active": True,
            "permissions": role_owner_permissions,
            "version": 1,
            "createdBy": SYSTEM_ACTOR,
            "createdAt": current_time,
        },
        {
            "_id": "rol_interested_client",
            "code": "INTERESTED_CLIENT",
            "name": "Interested Client",
            "description": "Client with preferences and matches",
            "type": "SYSTEM",
            "active": True,
            "permissions": role_interested_permissions,
            "version": 1,
            "createdBy": SYSTEM_ACTOR,
            "createdAt": current_time,
        },
        {
            "_id": "rol_supervisor",
            "code": "SUPERVISOR",
            "name": "Supervisor Testing",
            "description": "Custom testing: operational management without roles/audit",
            "type": "CUSTOM",
            "active": True,
            "permissions": role_supervisor_permissions,
            "version": 1,
            "createdBy": SYSTEM_ACTOR,
            "createdAt": current_time,
        },
        {
            "_id": "rol_viewer",
            "code": "VIEWER",
            "name": "Viewer Testing",
            "description": "Custom testing: read-only on all main resources",
            "type": "CUSTOM",
            "active": True,
            "permissions": role_viewer_permissions,
            "version": 1,
            "createdBy": SYSTEM_ACTOR,
            "createdAt": current_time,
        },
    ]


def build_testing_users_and_persons(current_time: datetime, role_id_map: dict) -> tuple[list[dict], list[dict]]:
    """Build 10 testing users and their corresponding person profiles."""
    ph = bcrypt_hash(TESTING_PASSWORD)
    birth_date = date(1990, 1, 1)
    
    users = []
    persons = []
    
    def add_user_person(user_id, first, last, email, user_type, role_code, **person_kwargs):
        role_id = role_id_map.get(role_code)
        if not role_id:
            raise Exception(f"Role code {role_code} not found in role_id_map!")
        
        users.append(make_user(
            _id=user_id,
            first=first,
            last=last,
            email=email,
            password_hash=ph,
            user_type=user_type,
            role_ids=[role_id],
            current_time=current_time
        ))
        
        person_type = user_type
        if user_type == "EMPLOYEE":
            person_type = "EMPLOYEE"
        elif user_type == "OWNER":
            person_type = "OWNER"
        elif user_type == "INTERESTED_CLIENT":
            person_type = "INTERESTED_CLIENT"
        
        persons.append(make_person(
            auth_user_id=user_id,
            first=first,
            last=last,
            email=email,
            phone=f"+591{hash(email) % 100000000:08d}",
            birth_date=birth_date,
            person_type=person_type,
            role_ids=[role_id],
            current_time=current_time,
            **person_kwargs
        ))
    
    add_user_person("usr_test_admin_01", "Testing Admin", "One", "admin1@admin", "ADMIN", "ADMIN")
    add_user_person("usr_test_admin_02", "Testing Admin", "Two", "admin2@admin", "ADMIN", "ADMIN")
    
    add_user_person("usr_test_agent_01", "Testing Agent", "One", "agent1@user", "EMPLOYEE", "AGENT",
                   department="Ventas", position="Agente Senior", hireDate=current_time)
    add_user_person("usr_test_agent_02", "Testing Agent", "Two", "agent2@user", "EMPLOYEE", "AGENT",
                   department="Alquileres", position="Agente Junior", hireDate=current_time)
    
    add_user_person("usr_test_owner_01", "Testing Owner", "One", "owner1@user", "OWNER", "OWNER",
                   taxId="NIT-123456789")
    add_user_person("usr_test_owner_02", "Testing Owner", "Two", "owner2@user", "OWNER", "OWNER",
                   taxId="NIT-987654321")
    
    add_user_person("usr_test_client_01", "Testing Client", "One", "client1@user",
                    "INTERESTED_CLIENT", "INTERESTED_CLIENT",
                    preferredContactMethod="EMAIL", budget="500000",
                    preferredZone="Zona Sur", preferredPropertyType="APARTAMENTO",
                    preferredRooms=2)

    add_user_person("usr_test_client_02", "Testing Client", "Two", "client2@user",
                    "INTERESTED_CLIENT", "INTERESTED_CLIENT",
                    preferredContactMethod="WHATSAPP", budget="750000",
                    preferredZone="Zona Norte", preferredPropertyType="CASA",
                    preferredRooms=3)
    
    add_user_person("usr_test_supervisor_01", "Testing Supervisor", "One", "supervisor1@user", "EMPLOYEE", "SUPERVISOR",
                   department="Operaciones", position="Supervisor", hireDate=current_time)
    add_user_person("usr_test_viewer_01", "Testing Viewer", "One", "viewer1@user", "EMPLOYEE", "VIEWER",
                   department="Auditoría", position="Viewer", hireDate=current_time)
    
    return users, persons


def seed_bootstrap_admin_and_person(db, current_time: datetime, role_id_map: dict) -> dict:
    """Seed master admin user and their person profile."""
    existing = db.users.find_one({"_id": "usr_admin_001"})
    admin_existed = existing is not None

    if admin_existed and not ROTATE_BOOTSTRAP_ADMIN_PASSWORD_ON_RESEED:
        password_hash = existing.get("passwordHash") or bcrypt_hash(BOOTSTRAP_ADMIN_PASSWORD)
    else:
        password_hash = bcrypt_hash(BOOTSTRAP_ADMIN_PASSWORD)

    admin_role_id = role_id_map.get("ADMIN")
    if not admin_role_id:
        raise Exception("ADMIN role not found in role_id_map!")

    admin_user = {
        "_id": "usr_admin_001",
        "firstName": "Super",
        "lastName": "Admin",
        "fullName": "Super Admin",
        "email": BOOTSTRAP_ADMIN_EMAIL,
        "emailNormalized": BOOTSTRAP_ADMIN_EMAIL.strip().lower(),
        "passwordHash": password_hash,
        "userType": "ADMIN",
        "status": "ACTIVE",
        "temporaryPassword": False,
        "temporaryPasswordExpiresAt": None,
        "mustChangePassword": False,
        "passwordChangedAt": None,
        "failedLoginAttempts": 0,
        "lockedUntil": None,
        "lastLoginAt": None,
        "primaryRoleIds": [admin_role_id],
        "activeEmploymentCycleId": None,
        "metadata": {},
        "updatedAt": current_time,
        "createdBy": SYSTEM_ACTOR,
    }

    if existing and "createdAt" in existing:
        admin_user["createdAt"] = existing["createdAt"]
    else:
        admin_user["createdAt"] = current_time

    db.users.replace_one(
        {"_id": "usr_admin_001"},
        admin_user,
        upsert=True,
    )
    
    admin_birth_date = date(1980, 1, 1)
    admin_person = make_person(
        auth_user_id="usr_admin_001",
        first="Super",
        last="Admin",
        email=BOOTSTRAP_ADMIN_EMAIL,
        phone="+59170000000",
        birth_date=admin_birth_date,
        person_type="ADMIN",
        role_ids=[admin_role_id],
        current_time=current_time
    )
    
    existing_person = db.persons.find_one({"_id": admin_person["_id"]})
    if existing_person and "createdAt" in existing_person:
        admin_person["createdAt"] = existing_person["createdAt"]
    
    db.persons.replace_one(
        {"_id": admin_person["_id"]},
        admin_person,
        upsert=True,
    )

    db.audit_events.update_one(
        {"_id": "aud_bootstrap_admin_created"},
        {
            "$set": {
                "eventType": "USER_CREATED",
                "actorUserId": SYSTEM_ACTOR,
                "targetType": "USER",
                "targetId": "usr_admin_001",
                "data": {
                    "roleIds": [admin_role_id],
                    "roleCodes": ["ADMIN"],
                    "bootstrap": True,
                    "email": BOOTSTRAP_ADMIN_EMAIL,
                    "passwordRotatedOnReseed": ROTATE_BOOTSTRAP_ADMIN_PASSWORD_ON_RESEED,
                },
                "occurredAt": current_time,
            }
        },
        upsert=True,
    )

    return {
        "admin_existed": admin_existed,
        "email": BOOTSTRAP_ADMIN_EMAIL,
        "password": BOOTSTRAP_ADMIN_PASSWORD,
        "password_rotated": (not admin_existed) or ROTATE_BOOTSTRAP_ADMIN_PASSWORD_ON_RESEED,
    }


def verify_seed(db) -> None:
    """Verify that all critical data was seeded correctly."""
    print("\n" + "="*58)
    print("  SEED VERIFICATION")
    print("="*58)
    
    roles_count = db.roles.count_documents({})
    print(f"Roles: {roles_count} (expected: 6)")
    
    master_admin = db.users.find_one({"_id": "usr_admin_001"})
    master_person = db.persons.find_one({"authUserId": "usr_admin_001"})
    
    if master_admin:
        role_ids = master_admin.get("primaryRoleIds", [])
        print(f"Master Admin: FOUND")
        print(f"  - Role IDs: {role_ids}")
        if master_person:
            print(f"  - Person profile: FOUND ✓")
        else:
            print(f"  - ❌ Person profile NOT FOUND!")
    else:
        print("❌ Master Admin NOT FOUND!")
    
    test_users = db.users.count_documents({"_id": {"$regex": "^usr_test_"}})
    test_persons = db.persons.count_documents({"authUserId": {"$regex": "^usr_test_"}})
    print(f"Test Users: {test_users} (expected: 10)")
    print(f"Test Persons: {test_persons} (expected: 10)")
    
    if test_users != test_persons:
        print(f"⚠️  Mismatch: {test_users} users but {test_persons} persons!")
    
    print("="*58 + "\n")


_TESTING_USERS_TABLE: list[tuple[str, str, str]] = [
    ("Admin 1",          "admin1@admin",      "ADMIN"),
    ("Admin 2",          "admin2@admin",      "ADMIN"),
    ("Agent 1",          "agent1@user",       "AGENT"),
    ("Agent 2",          "agent2@user",       "AGENT"),
    ("Owner 1",          "owner1@user",       "OWNER"),
    ("Owner 2",          "owner2@user",       "OWNER"),
    ("Client 1",         "client1@user",      "INTERESTED_CLIENT"),
    ("Client 2",         "client2@user",      "INTERESTED_CLIENT"),
    ("Supervisor 1",     "supervisor1@user",  "SUPERVISOR (custom)"),
    ("Viewer 1",         "viewer1@user",      "VIEWER (custom)"),
]


def print_summary(
    db,
    admin_info: dict,
    permissions_count: int,
    roles_count: int,
    total_users: int,
    total_persons: int,
) -> None:
    sep = "=" * 58
    print(f"\n{sep}")
    print("  SEED COMPLETED")
    print(sep)
    print(f"  DB           : {DB_NAME}")
    print(f"  Mongo URI    : {MONGO_URI}")
    print(f"  permissions  : {db.permissions_catalog.count_documents({})}")
    print(f"  roles        : {db.roles.count_documents({})}")
    print(f"  users        : {db.users.count_documents({})}")
    print(f"  persons      : {db.persons.count_documents({})}")
    print(f"  audit_events : {db.audit_events.count_documents({})}")
    print("-" * 58)
    print(f"  Permissions upserted : {permissions_count}")
    print(f"  Roles upserted       : {roles_count}  (4 system + 2 custom)")
    print(f"  Users upserted       : {total_users}  (1 master + 10 testing)")
    print(f"  Persons upserted     : {total_persons} (1 master + 10 testing)")
    print("-" * 58)
    print("  Master admin (permanent credentials):")
    print(f"    email    : {admin_info['email']}")
    if admin_info["password_rotated"]:
        print(f"    password : {admin_info['password']}")
    else:
        print("    password : not rotated (existing hash preserved)")
    print(f"    existed  : {admin_info['admin_existed']}")
    print("-" * 58)
    print(f"  Testing users  (shared password: {TESTING_PASSWORD})")
    print(f"  {'Label':<16} {'Email':<24} {'Role'}")
    print(f"  {'-'*15} {'-'*23} {'-'*22}")
    for label, email, role in _TESTING_USERS_TABLE:
        print(f"  {label:<16} {email:<24} {role}")
    print(sep + "\n")


def main() -> None:
    current_time = now_utc()
    client = None

    try:
        print("Connecting to MongoDB...")
        client = MongoClient(MONGO_URI)
        client.admin.command("ping")
        print("Connection OK.\n")

        db = client[DB_NAME]

        confirm_and_clear(db)
        drop_collection_indexes(db)

        print("Creating indexes...")
        ensure_indexes(db)
        print("Indexes created.\n")

        print("Seeding permissions catalog...")
        permissions = build_permissions(current_time)
        permissions_count = upsert_many_by_id(db.permissions_catalog, permissions, current_time)
        print(f"  ✓ {permissions_count} permission(s) processed\n")

        print("Seeding roles (system + custom)...")
        roles = build_roles(current_time)
        roles_count = upsert_many_by_id(db.roles, roles, current_time)
        
        role_id_map = {}
        for role in roles:
            role_id_map[role["code"]] = role["_id"]
        print(f"  ✓ {roles_count} role(s) processed")
        print(f"  ✓ Role ID map built: {role_id_map}\n")

        print("Seeding master admin user and person...")
        admin_info = seed_bootstrap_admin_and_person(db, current_time, role_id_map)
        print("  ✓ Master admin processed\n")

        print("Seeding testing users and persons...")
        testing_users, testing_persons = build_testing_users_and_persons(current_time, role_id_map)
        users_count = upsert_many_by_id(db.users, testing_users, current_time)
        persons_count = upsert_many_by_id(db.persons, testing_persons, current_time)
        print(f"  ✓ {users_count} testing user(s) processed")
        print(f"  ✓ {persons_count} testing person(s) processed\n")

        verify_seed(db)

        print_summary(db, admin_info, permissions_count, roles_count, 
                      1 + users_count, 1 + persons_count)

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