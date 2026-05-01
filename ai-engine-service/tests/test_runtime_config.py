import unittest

from app.core.config import Settings, validate_runtime_settings


class RuntimeConfigTests(unittest.TestCase):
    def test_validate_runtime_settings_fails_for_unsafe_prod_defaults(self) -> None:
        settings = Settings(
            APP_ENV="prod",
            SERVICE_AUTH_ENABLED=True,
            SERVICE_AUTH_TOKEN="genphish-internal-token",
            AI_PROVIDER="stub",
        )

        with self.assertRaises(RuntimeError):
            validate_runtime_settings(settings)

    def test_validate_runtime_settings_passes_for_safe_prod_config(self) -> None:
        settings = Settings(
            APP_ENV="prod",
            SERVICE_AUTH_ENABLED=True,
            SERVICE_AUTH_TOKEN="abcdefghijklmnopqrstuvwxyz123456",
            SERVICE_TOKEN_HEADER="X-Service-Token",
            COMPANY_HEADER="X-Company-Id",
            AI_PROVIDER="openai",
        )

        # openai key is validated lazily during provider initialization, not in config validator.
        validate_runtime_settings(settings)

    def test_header_alias_lists_are_merged(self) -> None:
        settings = Settings(
            SERVICE_TOKEN_HEADER="X-Service-Token",
            SERVICE_TOKEN_HEADER_ALIASES="X-Internal-Token, X-Proxy-Token",
            COMPANY_HEADER="X-Company-Id",
            COMPANY_HEADER_ALIASES="X-Tenant-Id",
        )

        self.assertEqual(
            settings.service_token_header_name_list,
            ["X-Service-Token", "X-Internal-Token", "X-Proxy-Token"],
        )
        self.assertEqual(
            settings.company_header_name_list,
            ["X-Company-Id", "X-Tenant-Id"],
        )


if __name__ == "__main__":
    unittest.main()
