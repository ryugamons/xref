#include "frida_detect.h"

#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <fstream>
#include <string>
#include <dirent.h>
#include <chrono>

namespace {

bool checkFridaPort() {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) return false;

    struct sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(27042);
    addr.sin_addr.s_addr = inet_addr("127.0.0.1");

    struct timeval timeout{0, 200000};
    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));

    bool detected = (connect(sock, reinterpret_cast<struct sockaddr*>(&addr), sizeof(addr)) == 0);
    close(sock);
    return detected;
}

bool checkMapsForFrida() {
    std::ifstream maps("/proc/self/maps");
    if (!maps.is_open()) return false;

    std::string line;
    while (std::getline(maps, line)) {
        if (line.find("frida") != std::string::npos ||
            line.find("gum-js-loop") != std::string::npos ||
            line.find("linjector") != std::string::npos) {
            return true;
        }
    }
    return false;
}

bool checkThreadNames() {
    DIR* dir = opendir("/proc/self/task");
    if (!dir) return false;

    struct dirent* entry;
    bool found = false;
    while ((entry = readdir(dir)) != nullptr) {
        std::string commPath = std::string("/proc/self/task/") + entry->d_name + "/comm";
        std::ifstream f(commPath);
        if (f.is_open()) {
            std::string name;
            std::getline(f, name);
            if (name.find("gum") != std::string::npos ||
                name.find("gmain") != std::string::npos) {
                found = true;
                break;
            }
        }
    }
    closedir(dir);
    return found;
}

}

bool detectFridaInstrumentation() {
    int signals = 0;
    if (checkFridaPort()) signals++;
    if (checkMapsForFrida()) signals++;
    if (checkThreadNames()) signals++;

    return signals >= 2;
}
