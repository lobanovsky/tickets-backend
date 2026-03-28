package ru.tickets.models

import kotlinx.serialization.Serializable

sealed class TicketsException(message: String) : Exception(message)

class NotFoundException(message: String = "Not found") : TicketsException(message)
class BadRequestException(message: String) : TicketsException(message)
class ConflictException(message: String) : TicketsException(message)
class ForbiddenException(message: String = "Forbidden") : TicketsException(message)

@Serializable
data class ErrorResponse(val code: String, val message: String?)
