package com.example.utilitymanager.data

import java.math.BigDecimal
import java.math.RoundingMode

/** Правила услуги из JSON-каталога + значения конкретного выбранного шаблона. */
data class ResolvedTemplateService(
    val key: String,
    val name: String,
    val unit: String,
    val price: BigDecimal?,
    val isFixed: Boolean
)

object TemplateBilling {
    fun activeServices(template: TemplateEntity, catalog: TemplateFieldsCatalog): List<ResolvedTemplateService> {
        val values = TemplateExtraSettings.read(template.extraSettingsJson)
        return catalog.services.mapNotNull { service ->
            val enabledDefault = catalog.fields.firstOrNull { it.id == service.enabledField }?.defaultValue
            if (service.enabledField.isNotBlank() &&
                (values[service.enabledField] ?: enabledDefault)?.toBooleanStrictOrNull() != true
            ) return@mapNotNull null
            val def = catalog.fields.firstOrNull { it.id == service.tariffField }
            val rawPrice = values[service.tariffField] ?: def?.defaultValue.orEmpty()
            val price = rawPrice.trim().replace(',', '.').toBigDecimalOrNull()
                ?.takeIf { it >= BigDecimal.ZERO }
            ResolvedTemplateService(
                key = service.key,
                name = service.name,
                unit = service.unit,
                price = price,
                isFixed = service.billingType == "fixed"
            )
        }
    }

    fun amount(usage: BigDecimal?, price: BigDecimal?): String? {
        if (usage == null || price == null) return null
        return usage.multiply(price).setScale(2, RoundingMode.HALF_UP).toPlainString()
    }

    fun normalized(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    /**
     * Запись текущего расчётного месяца. Ранее зафиксированная цена не меняется:
     * менять исторические тарифы можно будет отдельным действием перерасчёта.
     */
    fun meterRow(
        apartmentId: Long,
        month: String,
        key: String,
        defaultName: String,
        defaultUnit: String,
        previous: BigDecimal,
        current: BigDecimal,
        previousRow: MonthlyChargeEntity?,
        service: ResolvedTemplateService?
    ): MonthlyChargeEntity {
        val usage = current.subtract(previous)
        val price = previousRow?.unitPrice?.toBigDecimalOrNull() ?: service?.price
        return MonthlyChargeEntity(
            apartmentId = apartmentId,
            yearMonth = month,
            serviceKey = key,
            serviceName = previousRow?.serviceName ?: service?.name ?: defaultName,
            unit = previousRow?.unit ?: service?.unit ?: defaultUnit,
            previousReading = normalized(previous),
            currentReading = normalized(current),
            usage = normalized(usage),
            unitPrice = price?.let(::normalized),
            amount = amount(usage, price)
        )
    }

    /** Для фиксированных услуг расход 1 месяц; сумма = плата за месяц. */
    fun fixedRow(
        apartmentId: Long,
        month: String,
        service: ResolvedTemplateService,
        previousRow: MonthlyChargeEntity?
    ): MonthlyChargeEntity {
        val price = previousRow?.unitPrice?.toBigDecimalOrNull() ?: service.price
        return MonthlyChargeEntity(
            apartmentId = apartmentId,
            yearMonth = month,
            serviceKey = service.key,
            serviceName = previousRow?.serviceName ?: service.name,
            unit = previousRow?.unit ?: service.unit,
            usage = "1",
            unitPrice = price?.let(::normalized),
            amount = amount(BigDecimal.ONE, price)
        )
    }

    /** Дополняем только отсутствующие тарифы; ранее сохранённые суммы не пересчитываем. */
    fun fillMissingPrices(
        existing: List<MonthlyChargeEntity>,
        services: List<ResolvedTemplateService>
    ): List<MonthlyChargeEntity> {
        val byKey = services.associateBy { it.key }
        return existing.mapNotNull { row ->
            val service = byKey[row.serviceKey] ?: return@mapNotNull null
            val rate = row.unitPrice?.toBigDecimalOrNull() ?: service.price
            val usage = row.usage?.toBigDecimalOrNull()
            val sum = row.amount ?: amount(usage, rate)
            if (rate == null || (row.unitPrice != null && row.amount == sum)) null
            else row.copy(unitPrice = row.unitPrice ?: normalized(rate), amount = sum)
        }
    }
}
