provider "azurerm" {
  features {}
}

locals {
  vaultName = "${var.product}-${var.env}"
}

resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = var.location

  tags = var.common_tags
}

data "azurerm_user_assigned_identity" "rpe-shared-identity" {
  name                = "rpe-shared-${var.env}-mi"
  resource_group_name = "managed-identities-${var.env}-rg"
}

resource "azurerm_application_insights" "appinsights" {
  name                = "${var.product}-${var.component}-appinsights-${var.env}"
  location            = var.appinsights_location
  resource_group_name = azurerm_resource_group.rg.name
  application_type    = "web"

  tags = var.common_tags

  lifecycle {
    ignore_changes = [
      # Ignore changes to appinsights as otherwise upgrading to the Azure provider 2.x
      # destroys and re-creates this appinsights instance
      application_type,
    ]
  }
}
