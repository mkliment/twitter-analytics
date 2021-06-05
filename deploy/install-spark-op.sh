#!/usr/bin/env bash

set -e

K8S_CONTEXT=local

sparkOperatorVersion=latest

namespaceSparkOperStream=spark-op

namespaceStreaming=streaming

detect_sparkoperator() {
    kubectl --context="${K8S_CONTEXT}" get deployment -l app.kubernetes.io/name=spark-operator --all-namespaces --ignore-not-found=true --no-headers | awk -v i=1 -v j=1 'FNR == i {print $j}'
}

install_spark_operator() {

  helm --kube-context "${K8S_CONTEXT}" repo update
  helm --kube-context "${K8S_CONTEXT}" repo add spark-operator https://googlecloudplatform.github.io/spark-on-k8s-operator

  # install for all other Spark streaming apps in streaming namespace
  helm --kube-context "${K8S_CONTEXT}" install so-stream spark-operator/spark-operator --namespace $namespaceSparkOperStream \
  --set batchScheduler.enable=true \
  --set image.tag=$sparkOperatorVersion \
  --set webhook.enable=true \
  --set webhook.namespaceSelector="namespace=$namespaceStreaming" \
  --set metrics.enable=true \
  --set sparkJobNamespace=$namespaceStreaming \
  --set logLevel=3
  # we need to secret "gitlab-registry" and add that to serviceAccount so we can pull images with that sa. Secret should be created once in each namespace
  kubectl --context="${K8S_CONTEXT}" -n $namespaceStreaming patch serviceaccount so-stream -p '{"imagePullSecrets": [{"name": "gitlab-registry"}]}'
}

setup_spark_operator() {
  sparkOperatorNamespace=$(detect_sparkoperator)
  export installSparkOperator=false
  if [ -z "$sparkOperatorNamespace" ]
  then
      echo "The spark operator is not installed, will install in $namespaceSparkOperStream"
      installSparkOperator=true
      sparkOperatorNamespace=$namespaceSparkOperStream
  else
      echo "Spark operator is already installed in '$sparkOperatorNamespace'"

  fi

  if [ "$installSparkOperator" = true ]; then
      echo "Installing Spark operator version = '$sparkOperatorVersion'"
      result=$(install_spark_operator)

      if [ $? -ne 0 ]; then
          print_error_message "$result"
          print_error_message "installation failed"
          exit 1
      fi
      echo "Finished installation of Spark operator"
  fi
}

createNamespaces(){

  kubectl --context="${K8S_CONTEXT}" create namespace $namespaceSparkOperStream --dry-run -o yaml | kubectl --context="${K8S_CONTEXT}" apply -f - &
  wait $!
  kubectl --context="${K8S_CONTEXT}" create namespace $namespaceStreaming --dry-run -o yaml | kubectl --context="${K8S_CONTEXT}" apply -f - &
  wait $!
}

createNamespaces &
wait $!
setup_spark_operator &
wait $!
