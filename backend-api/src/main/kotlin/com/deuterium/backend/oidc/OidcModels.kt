package com.deuterium.backend.oidc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OidcDiscoveryResponse(
    val issuer: String,
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String,
    @SerialName("token_endpoint")
    val tokenEndpoint: String,
    @SerialName("userinfo_endpoint")
    val userInfoEndpoint: String,
    @SerialName("jwks_uri")
    val jwksUri: String,
    @SerialName("response_types_supported")
    val responseTypesSupported: List<String> = listOf("code"),
    @SerialName("subject_types_supported")
    val subjectTypesSupported: List<String> = listOf("public"),
    @SerialName("id_token_signing_alg_values_supported")
    val idTokenSigningAlgValuesSupported: List<String> = listOf("RS256"),
    @SerialName("scopes_supported")
    val scopesSupported: List<String> = listOf("openid", "profile", "email"),
    @SerialName("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String> = listOf("client_secret_basic", "client_secret_post"),
    @SerialName("claims_supported")
    val claimsSupported: List<String> = listOf("sub", "email", "displayName", "preferred_username", "name"),
)

@Serializable
data class JwksResponse(val keys: List<JwkKey>)

@Serializable
data class JwkKey(
    val kty: String = "RSA",
    val use: String = "sig",
    val kid: String,
    val alg: String = "RS256",
    val n: String,
    val e: String,
)

@Serializable
data class OidcTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String = "Bearer",
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("id_token")
    val idToken: String,
    val scope: String,
)

@Serializable
data class OAuthErrorResponse(
    val error: String,
    @SerialName("error_description")
    val errorDescription: String? = null,
)

@Serializable
data class OidcProfile(
    val sub: String,
    val email: String,
    val displayName: String,
    @SerialName("preferred_username")
    val preferredUsername: String,
    val name: String,
)
