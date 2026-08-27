@{
    BaseUrl = "http://3.35.11.125"
    SshHost = "ubuntu@3.35.11.125"
    SshIdentityFile = "<absolute-path-to-ec2-private-key>"
    RemoteProjectDirectory = "/home/ubuntu/actions-runner/_work/backend/backend"

    # Optional. Leave empty when JAVA_HOME already points to JDK 21.
    JavaHome = ""

    ResultRoot = "load-tests/results/aws"
    DockerContainer = "backend-was-1"
    BackendImage = "malhaebom/backend:latest"
    Scenarios = @(
        @{
            Name = "baseline-retry0"
            Stages = @(10, 100, 200, 300)
            ClientMaxRetries = 0
        }
        @{
            Name = "retry1"
            Stages = @(10, 100, 200, 300)
            ClientMaxRetries = 1
        }
        @{
            Name = "retry2"
            Stages = @(10, 100, 200, 300)
            ClientMaxRetries = 2
        }
    )
    AssessmentQueueCapacity = 64
    AssessmentMaxQueueWaitSeconds = 10
    RecoveryTimeoutSeconds = 300

    LocalApiPort = 18080
    LocalManagementPort = 19090
    LocalPrometheusPort = 19091
    LocalGrafanaPort = 13000
    ReadinessTimeoutSeconds = 120
}
