package com.nexuscraft.nexushouses;

/** A member's standing within their own house. Exactly one HEAD at a time (enforced by
 * HouseEngine, not by this enum); at most one HEIR designated at a time; everyone else is SWORN. */
public enum HouseRank {
    HEAD, HEIR, SWORN
}
